package com.langlez.chat.application

import com.langlez.chat.api.ChatResponse
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.chat.infrastructure.outbox.ChatOutBoxRepository
import com.langlez.core.FileStorage
import com.langlez.core.LanglezException
import com.langlez.member.domain.MemberRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
@Transactional
class ChatService(
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val memberRepository: MemberRepository,
    private val fileStorage: FileStorage,
    private val chatBroadcaster: ChatBroadcaster,
    private val chatRoomCreator: ChatRoomCreator,
    private val chatOutBoxRepository: ChatOutBoxRepository,
) {

    fun getOrCreateRoom(memberId: Long, targetUsername: String): ChatRoom {
        val targetMember = memberRepository.findByUsername(targetUsername)
            ?: throw LanglezException(404, "member.not-found")

        val sortedIds = listOf(memberId, targetMember.id).sorted()
        val existingRoom = chatRoomRepository.findByParticipants(sortedIds[0], sortedIds[1])
        if (existingRoom != null) {
            return existingRoom
        }

        return chatRoomCreator.getOrCreateRoom(memberId, sortedIds[1], sortedIds[0])
    }

    fun toRoomSummary(room: ChatRoom, memberId: Long): ChatResponse.RoomSummary {
        val targetId = room.participantIds.firstOrNull { it != memberId } ?: memberId
        val target = memberRepository.findById(targetId)
            ?: throw LanglezException(404, "member.not-found")

        val lastReadAt = room.readStatus[memberId] ?: Instant.EPOCH
        val unreadCount = chatMessageRepository.countUnread(room.id!!, memberId, lastReadAt)

        return ChatResponse.RoomSummary(
            id = room.id!!,
            targetUsername = target.username,
            targetNickname = target.nickname,
            lastMessageAt = room.lastMessageAt,
            lastMessagePreview = room.lastMessagePreview,
            unreadCount = unreadCount,
            createdAt = room.createdAt
        )
    }

    fun sendMessage(
        memberId: Long,
        roomId: String,
        type: ChatMessage.Type,
        content: String?,
        fileUrl: String?
    ): ChatMessage {
        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")

        if (!room.hasParticipant(memberId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }

        val sender = memberRepository.findById(memberId)
            ?: throw LanglezException(404, "member.not-found")

        val now = Instant.now()
        val message = ChatMessage(
            roomId = roomId,
            senderId = memberId,
            type = type,
            content = content,
            fileUrl = fileUrl,
            createdAt = now
        )
        val savedMessage = chatMessageRepository.save(message)

        if (type != ChatMessage.Type.TEXT && fileUrl != null) {
            chatOutBoxRepository.save(
                aggregateType = "CHAT_MESSAGE",
                aggregateId = roomId,
                eventName = "chat-attachment-uploaded",
                payload = ChatAttachmentUploadedEvent(
                    roomId = roomId,
                    uploaderId = memberId,
                    storageKey = fileUrl,
                    fileType = type.name,
                ),
            )
        }

        val preview = when (type) {
            ChatMessage.Type.TEXT -> content ?: ""
            ChatMessage.Type.IMAGE -> "[IMAGE]"
            ChatMessage.Type.VIDEO -> "[VIDEO]"
            ChatMessage.Type.AUDIO -> "[AUDIO]"
        }

        room.updateLastMessage(preview, savedMessage.createdAt)
        room.updateReadStatus(memberId, savedMessage.createdAt)
        chatRoomRepository.save(room)

        val broadcastPayload = ChatMessageBroadcastPayload(
            id = savedMessage.id,
            roomId = roomId,
            senderUsername = sender.username,
            type = type,
            content = content,
            fileUrl = fileUrl,
            createdAt = savedMessage.createdAt
        )
        chatBroadcaster.broadcastMessage(roomId, broadcastPayload)

        return savedMessage
    }

    fun markAsRead(memberId: Long, roomId: String) {
        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")

        if (!room.hasParticipant(memberId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }

        val member = memberRepository.findById(memberId)
            ?: throw LanglezException(404, "member.not-found")

        val now = Instant.now()
        chatRoomRepository.updateReadStatus(roomId, memberId, now)

        chatBroadcaster.broadcastRead(roomId, member.username, now)
    }

    @Transactional(readOnly = true)
    fun getRooms(memberId: Long, cursor: String?, size: Int): ChatResponse.RoomCursorList {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val rooms = chatRoomRepository.findByParticipant(memberId, cursor, boundedSize)

        val otherIds = rooms.flatMap { it.participantIds }.distinct()
        val membersMap = memberRepository.findByIds(otherIds).associateBy { it.id }

        val summaries = rooms.map { room ->
            val targetId = room.participantIds.firstOrNull { it != memberId } ?: memberId
            val target = membersMap[targetId]
            val targetUsername = target?.username ?: "unknown"
            val targetNickname = target?.nickname ?: "Unknown"

            val lastReadAt = room.readStatus[memberId] ?: Instant.EPOCH
            val unreadCount = chatMessageRepository.countUnread(room.id!!, memberId, lastReadAt)

            ChatResponse.RoomSummary(
                id = room.id!!,
                targetUsername = targetUsername,
                targetNickname = targetNickname,
                lastMessageAt = room.lastMessageAt,
                lastMessagePreview = room.lastMessagePreview,
                unreadCount = unreadCount,
                createdAt = room.createdAt
            )
        }

        val nextCursor = if (rooms.size == boundedSize) rooms.lastOrNull()?.id else null
        return ChatResponse.RoomCursorList(nextCursor, summaries)
    }

    @Transactional(readOnly = true)
    fun getMessages(memberId: Long, roomId: String, cursor: String?, size: Int): ChatResponse.MessageCursorList {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")

        if (!room.hasParticipant(memberId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }

        val messages = chatMessageRepository.findByRoom(roomId, cursor, boundedSize)

        val senderIds = messages.map { it.senderId }.distinct()
        val sendersMap = memberRepository.findByIds(senderIds).associateBy { it.id }

        val summaries = messages.map { msg ->
            val sender = sendersMap[msg.senderId]
            val senderUsername = sender?.username ?: "unknown"

            ChatResponse.MessageSummary(
                id = msg.id!!,
                senderUsername = senderUsername,
                type = msg.type,
                content = msg.content,
                fileUrl = msg.fileUrl,
                createdAt = msg.createdAt
            )
        }

        val nextCursor = if (messages.size == boundedSize) messages.lastOrNull()?.id else null
        return ChatResponse.MessageCursorList(nextCursor, summaries)
    }

    fun generateUploadUrl(filename: String, contentType: String): String {
        return fileStorage.generateUploadUrl(filename, contentType, "chat")
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}

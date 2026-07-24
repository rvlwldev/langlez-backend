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
        fileUrl: String?,
        replyToMessageId: String? = null,
    ): ChatMessage {
        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")

        if (!room.hasParticipant(memberId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }

        if (replyToMessageId != null) {
            val replyTarget = chatMessageRepository.findById(replyToMessageId)
            if (replyTarget == null || replyTarget.roomId != roomId) {
                throw LanglezException(404, "chat.reply-target-not-found")
            }
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
            replyToMessageId = replyToMessageId,
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

        chatRoomRepository.updateLastMessageAndReadStatus(roomId, preview, savedMessage.createdAt, memberId)

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

    fun deleteMessage(memberId: Long, roomId: String, messageId: String) {
        val message = chatMessageRepository.findById(messageId)
            ?: throw LanglezException(404, "chat.message-not-found")

        if (message.roomId != roomId) {
            throw LanglezException(404, "chat.message-not-found")
        }

        if (message.senderId != memberId) {
            throw LanglezException(403, "chat.not-sender")
        }

        if (message.deletedAt != null) {
            throw LanglezException(409, "chat.already-deleted")
        }

        val now = Instant.now()
        chatMessageRepository.markDeleted(messageId, now)
        chatBroadcaster.broadcastMessageDeleted(roomId, messageId)
    }

    fun reportUser(reporterId: Long, roomId: String, reportedUserId: Long, reason: String) {
        val room = chatRoomRepository.findById(roomId)
            ?: throw LanglezException(404, "chat.room-not-found")

        if (!room.hasParticipant(reporterId)) {
            throw LanglezException(403, "chat.room-forbidden")
        }

        if (!room.hasParticipant(reportedUserId)) {
            throw LanglezException(400, "chat.reported-user-not-participant")
        }

        val lastMessage = chatMessageRepository.findLastMessage(roomId)
        val triggerMessageId = lastMessage?.id

        chatOutBoxRepository.save(
            aggregateType = "CHAT_REPORT",
            aggregateId = roomId,
            eventName = "chat-user-reported",
            payload = ChatUserReportedEvent(
                roomId = roomId,
                reporterId = reporterId,
                reportedUserId = reportedUserId,
                reason = reason,
                triggerMessageId = triggerMessageId,
            )
        )
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
        val unreadMap = chatMessageRepository.countUnreadBatch(rooms, memberId)

        val summaries = rooms.map { room ->
            val targetId = room.participantIds.firstOrNull { it != memberId } ?: memberId
            val target = membersMap[targetId]
            val targetUsername = target?.username ?: "unknown"
            val targetNickname = target?.nickname ?: "Unknown"

            val unreadCount = unreadMap[room.id!!] ?: 0L

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

        val nextCursor = if (rooms.size == boundedSize) {
            rooms.lastOrNull()?.let { r ->
                val timeStr = r.lastMessageAt?.toEpochMilli()?.toString() ?: "null"
                "${timeStr}_${r.id}"
            }
        } else null
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

        val replyIds = messages.mapNotNull { it.replyToMessageId }.distinct()
        val replyMessagesMap = if (replyIds.isNotEmpty()) {
            chatMessageRepository.findByIds(replyIds).associateBy { it.id!! }
        } else emptyMap()

        val allSenderIds = (messages.map { it.senderId } + replyMessagesMap.values.map { it.senderId }).distinct()
        val sendersMap = memberRepository.findByIds(allSenderIds).associateBy { it.id }

        val summaries = messages.map { msg ->
            val sender = sendersMap[msg.senderId]
            val senderUsername = sender?.username ?: "unknown"

            val replyPreview = msg.replyToMessageId?.let { replyId ->
                val replyTarget = replyMessagesMap[replyId]
                if (replyTarget != null) {
                    if (replyTarget.deletedAt != null) {
                        ChatResponse.ReplyPreview(
                            messageId = replyId,
                            senderUsername = "알 수 없음",
                            type = replyTarget.type,
                            contentPreview = null,
                            deleted = true
                        )
                    } else {
                        val replySender = sendersMap[replyTarget.senderId]
                        val replySenderUsername = replySender?.username ?: "알 수 없음"
                        val contentPreview = if (replyTarget.type == ChatMessage.Type.TEXT) replyTarget.content?.take(100) else null
                        ChatResponse.ReplyPreview(
                            messageId = replyId,
                            senderUsername = replySenderUsername,
                            type = replyTarget.type,
                            contentPreview = contentPreview,
                            deleted = false
                        )
                    }
                } else null
            }

            val isDeleted = msg.deletedAt != null
            ChatResponse.MessageSummary(
                id = msg.id!!,
                senderUsername = senderUsername,
                type = msg.type,
                content = if (isDeleted) null else msg.content,
                fileUrl = if (isDeleted) null else msg.fileUrl,
                createdAt = msg.createdAt,
                replyPreview = replyPreview,
                deleted = isDeleted
            )
        }

        val nextCursor = if (messages.size == boundedSize) {
            messages.lastOrNull()?.let { m ->
                "${m.createdAt.toEpochMilli()}_${m.id}"
            }
        } else null
        return ChatResponse.MessageCursorList(nextCursor, summaries)
    }

    @Transactional(readOnly = true)
    fun toMessageSummary(message: ChatMessage): ChatResponse.MessageSummary {
        val sender = memberRepository.findById(message.senderId)
        val senderUsername = sender?.username ?: "unknown"

        val replyPreview = message.replyToMessageId?.let { replyId ->
            val replyTarget = chatMessageRepository.findById(replyId)
            if (replyTarget != null) {
                if (replyTarget.deletedAt != null) {
                    ChatResponse.ReplyPreview(
                        messageId = replyId,
                        senderUsername = "알 수 없음",
                        type = replyTarget.type,
                        contentPreview = null,
                        deleted = true
                    )
                } else {
                    val replySender = memberRepository.findById(replyTarget.senderId)
                    val replySenderUsername = replySender?.username ?: "알 수 없음"
                    val contentPreview = if (replyTarget.type == ChatMessage.Type.TEXT) replyTarget.content?.take(100) else null
                    ChatResponse.ReplyPreview(
                        messageId = replyId,
                        senderUsername = replySenderUsername,
                        type = replyTarget.type,
                        contentPreview = contentPreview,
                        deleted = false
                    )
                }
            } else null
        }

        val isDeleted = message.deletedAt != null
        return ChatResponse.MessageSummary(
            id = message.id!!,
            senderUsername = senderUsername,
            type = message.type,
            content = if (isDeleted) null else message.content,
            fileUrl = if (isDeleted) null else message.fileUrl,
            createdAt = message.createdAt,
            replyPreview = replyPreview,
            deleted = isDeleted
        )
    }

    fun generateUploadUrl(filename: String, contentType: String): String {
        return fileStorage.generateUploadUrl(filename, contentType, "chat")
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
    }
}

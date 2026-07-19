package com.langlez.admin.application

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.core.MemberPresenceTracker
import com.langlez.member.domain.MemberRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class AdminService(
    private val memberRepository: MemberRepository,
    private val memberPresenceTracker: MemberPresenceTracker,
    private val chatRoomRepository: ChatRoomRepository,
    private val chatMessageRepository: ChatMessageRepository,
    private val attachmentRepository: AttachmentRepository,
) {

    fun getDashboard(): AdminDashboardView {
        val totalMembers = memberRepository.countAll()
        val onlineMembers = memberPresenceTracker.countOnline()
        return AdminDashboardView(totalMembers, onlineMembers)
    }

    fun getUsers(cursor: Long?, size: Int): List<AdminUserRow> {
        val members = memberRepository.findAll(cursor, size)
        return members.map { member ->
            val online = memberPresenceTracker.isOnline(member.id)
            AdminUserRow(
                id = member.id,
                username = member.username,
                nickname = member.nickname,
                createdAt = member.createdAt,
                online = online
            )
        }
    }

    fun getUserChats(username: String): List<AdminChatRoomRow> {
        val member = memberRepository.findByUsername(username)
            ?: throw LanglezException(status = 404, message = "User not found")
        val rooms = chatRoomRepository.findByParticipant(member.id, null, 100)
        
        // 1+N 방지를 위해 상대방 멤버 목록 일괄 조회
        val recipientIds = rooms.map { it.getRecipientId(member.id) }.distinct()
        val recipientsMap = memberRepository.findByIds(recipientIds).associateBy { it.id }

        return rooms.map { room ->
            val recipientId = room.getRecipientId(member.id)
            val recipient = recipientsMap[recipientId]
            val participantUsernames = listOf(username, recipient?.username ?: "Unknown")
            AdminChatRoomRow(
                roomId = room.id!!,
                participantUsernames = participantUsernames,
                lastMessagePreview = room.lastMessagePreview,
                lastMessageAt = room.lastMessageAt
            )
        }
    }

    fun getAllChats(cursor: String?, size: Int): List<AdminChatRoomRow> {
        val rooms = chatRoomRepository.findAllRooms(cursor, size)
        
        // 1+N 방지
        val allParticipantIds = rooms.flatMap { it.participantIds }.distinct()
        val membersMap = memberRepository.findByIds(allParticipantIds).associateBy { it.id }

        return rooms.map { room ->
            val participantUsernames = room.participantIds.map { id ->
                membersMap[id]?.username ?: "Unknown"
            }
            AdminChatRoomRow(
                roomId = room.id!!,
                participantUsernames = participantUsernames,
                lastMessagePreview = room.lastMessagePreview,
                lastMessageAt = room.lastMessageAt
            )
        }
    }

    fun getChatRoomMessages(roomId: String, cursor: String?, size: Int): List<AdminMessageRow> {
        val messages = chatMessageRepository.findByRoom(roomId, cursor, size)
        
        // 1+N 방지
        val senderIds = messages.map { it.senderId }.distinct()
        val sendersMap = memberRepository.findByIds(senderIds).associateBy { it.id }

        // 오래된 -> 최신 순으로 정렬하기 위해 reversed
        return messages.reversed().map { message ->
            val senderUsername = sendersMap[message.senderId]?.username ?: "Unknown"
            AdminMessageRow(
                id = message.id,
                roomId = message.roomId,
                senderUsername = senderUsername,
                type = message.type.name,
                content = message.content,
                fileUrl = message.fileUrl,
                createdAt = message.createdAt
            )
        }
    }

    fun getChatRoomMessagesSince(roomId: String, since: Instant): List<AdminMessageRow> {
        val messages = chatMessageRepository.findByRoomSince(roomId, since)
        
        // 1+N 방지
        val senderIds = messages.map { it.senderId }.distinct()
        val sendersMap = memberRepository.findByIds(senderIds).associateBy { it.id }

        return messages.map { message ->
            val senderUsername = sendersMap[message.senderId]?.username ?: "Unknown"
            AdminMessageRow(
                id = message.id,
                roomId = message.roomId,
                senderUsername = senderUsername,
                type = message.type.name,
                content = message.content,
                fileUrl = message.fileUrl,
                createdAt = message.createdAt
            )
        }
    }

    fun getAttachments(
        cursor: Long?,
        size: Int,
        sourceType: Attachment.SourceType? = null,
        fileType: Attachment.FileType? = null,
    ): List<AdminAttachmentRow> {
        val attachments = attachmentRepository.findAll(cursor, size, sourceType, null, fileType)

        // 1+N 방지
        val uploaderIds = attachments.map { it.uploaderId }.distinct()
        val uploadersMap = memberRepository.findByIds(uploaderIds).associateBy { it.id }

        return attachments.map { attachment ->
            AdminAttachmentRow(
                id = attachment.id,
                uploaderUsername = uploadersMap[attachment.uploaderId]?.username ?: "Unknown",
                sourceType = attachment.sourceType.name,
                sourceId = attachment.sourceId,
                fileType = attachment.fileType.name,
                storageKey = attachment.storageKey,
                createdAt = attachment.createdAt
            )
        }
    }
}

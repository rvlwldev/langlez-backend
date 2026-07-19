package com.langlez.admin.application

import java.time.Instant

data class AdminDashboardView(
    val totalMembers: Long,
    val onlineMembers: Long
)

data class AdminUserRow(
    val id: Long,
    val username: String,
    val nickname: String,
    val createdAt: Instant,
    val online: Boolean
)

data class AdminChatRoomRow(
    val roomId: String,
    val participantUsernames: List<String>,
    val lastMessagePreview: String?,
    val lastMessageAt: Instant?
)

data class AdminMessageRow(
    val id: String?,
    val roomId: String,
    val senderUsername: String,
    val type: String,
    val content: String?,
    val fileUrl: String?,
    val createdAt: Instant,
    val deleted: Boolean
)

data class AdminAttachmentRow(
    val id: Long,
    val uploaderUsername: String,
    val sourceType: String,
    val sourceId: String,
    val fileType: String,
    val storageKey: String,
    val createdAt: Instant
)

data class AdminPostRow(
    val id: Long,
    val authorUsername: String,
    val content: String,
    val deleted: Boolean,
    val blinded: Boolean,
    val likeCount: Long,
    val reportCount: Int,
    val createdAt: Instant
)

data class AdminCommentRow(
    val id: Long,
    val authorUsername: String,
    val content: String,
    val deleted: Boolean,
    val createdAt: Instant
)

data class AdminReportRow(
    val id: Long,
    val reporterUsername: String,
    val reportedUsername: String,
    val sourceType: String,
    val sourceId: String,
    val reason: String,
    val triggerMessageId: String?,
    val createdAt: Instant
)

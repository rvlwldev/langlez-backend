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
    val createdAt: Instant
)

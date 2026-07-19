package com.langlez.chat.application

data class ChatUserReportedEvent(
    val roomId: String,
    val reporterId: Long,
    val reportedUserId: Long,
    val reason: String,
    val triggerMessageId: String?,
)

package com.langlez.echo.application

data class EchoPostReportedEvent(
    val postId: String,
    val reporterId: Long,
    val reportedUserId: Long,
    val reason: String,
)

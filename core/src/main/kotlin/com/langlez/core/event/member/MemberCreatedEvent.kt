package com.langlez.core.event.member

data class MemberCreatedEvent(
    val id: Long,
    val email: String,
    val username: String,
    val nickname: String,
)

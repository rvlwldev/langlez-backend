package com.langlez.core.event.member

data class MemberCreatedEvent(
    val id: Long,
    val email: String,
    val handle: String,
    val nickname: String,
)

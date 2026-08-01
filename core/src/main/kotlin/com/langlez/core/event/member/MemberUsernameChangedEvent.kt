package com.langlez.core.event.member

data class MemberUsernameChangedEvent(
    val id: Long,
    val newUsername: String,
)

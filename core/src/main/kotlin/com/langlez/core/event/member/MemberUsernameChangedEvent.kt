package com.langlez.core.event.member

data class MemberUsernameChangedEvent(
    val id: Long,
    val oldUsername: String,
    val newUsername: String,
)

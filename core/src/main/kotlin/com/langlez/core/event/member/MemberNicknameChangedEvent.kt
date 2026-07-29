package com.langlez.core.event.member

data class MemberNicknameChangedEvent(
    val id: Long,
    val newNickname: String,
)

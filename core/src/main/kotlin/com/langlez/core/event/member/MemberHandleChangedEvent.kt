package com.langlez.core.event.member

data class MemberHandleChangedEvent(
    val id: Long,
    val newHandle: String,
)

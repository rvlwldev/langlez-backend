package com.langlez.member.contract

data class MemberHandleChangedEvent(
    val id: Long,
    val newHandle: String,
)

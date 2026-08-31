package com.langlez.member.contract

data class MemberCreatedEvent(
    val id: Long,
    val email: String,
    val handle: String,
)

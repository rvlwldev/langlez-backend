package com.langlez.member.domain

sealed interface MemberEvent {
    data class Created(val id: Long, val email: String, val username: String, val nickname: String) : MemberEvent
    data class Login(val id: Long)
}
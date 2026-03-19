package com.langlez.member.application


sealed interface MemberEvent {
    data class Created(val id: Long, val email: String, val username: String, val nickname: String) : MemberEvent
}
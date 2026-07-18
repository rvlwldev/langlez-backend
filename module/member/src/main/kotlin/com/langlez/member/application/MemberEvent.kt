package com.langlez.member.application


sealed interface MemberEvent {
    data class Created(val id: Long, val email: String, val username: String, val nickname: String) : MemberEvent
    data class UsernameChanged(val id: Long, val newUsername: String) : MemberEvent
    data class NicknameChanged(val id: Long, val newNickname: String) : MemberEvent
}
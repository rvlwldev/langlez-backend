package com.langlez.member.application.command

import com.langlez.member.domain.embedded.MemberProvider

/** 회원 생성 시 필수 정보 (OAuth 로그인 시 사용) */
data class CreateMemberCommand(
        val email: String,
        val nickname: String,
        val agreeTerm: Boolean,

        // Provider 정보
        val providerId: String,
        val providerType: MemberProvider.Type,
        val providerUserName: String,
)

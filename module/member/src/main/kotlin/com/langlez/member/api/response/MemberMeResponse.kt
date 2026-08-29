package com.langlez.member.api.response

import com.langlez.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant
import java.time.LocalDate

data class MemberMeResponse(
    @field:Schema(description = "이메일") val email: String,
    @field:Schema(description = "handle(고유 아이디)") val handle: String,
    @field:Schema(description = "닉네임 (표시용 이름, handle 과 달리 유니크하지 않다)", nullable = true) val nickname: String?,
    @field:Schema(description = "권한", example = "MEMBER") val role: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val imageUrl: String?,
    // 개인식별 정보는 프로필이 아니라 계정 소유다. 수정도 PATCH /api/v1/members/me 로만 한다.
    @field:Schema(description = "성별", example = "SECRET") val gender: String,
    @field:Schema(description = "생년월일", nullable = true) val birthDay: LocalDate?,
    @field:Schema(description = "국가 코드 (ISO 3166-1 alpha-2)", nullable = true) val country: String?,
    @field:Schema(description = "가입일시") val createdAt: Instant,
    @field:Schema(description = "마지막 접속일시", nullable = true) val lastAccessedAt: Instant?,
) {
    constructor(member: Member) : this(
        email = member.email,
        handle = member.handle,
        nickname = member.nickname,
        role = member.role.name,
        imageUrl = member.imageUrl,
        gender = member.gender.name,
        birthDay = member.birthDay,
        country = member.country,
        createdAt = member.createdAt,
        lastAccessedAt = member.audit.lastAccessedAt,
    )
}

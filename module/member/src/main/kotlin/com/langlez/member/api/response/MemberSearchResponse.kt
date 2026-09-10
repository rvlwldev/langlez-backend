package com.langlez.member.api.response

import com.langlez.member.domain.Member
import io.swagger.v3.oas.annotations.media.Schema

data class MemberSearchResponse(
    @field:Schema(description = "회원 id") val id: Long,
    @field:Schema(description = "handle(고유 아이디)") val handle: String,
    @field:Schema(description = "닉네임 (표시용 이름)", nullable = true) val nickname: String?,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val imageUrl: String?,
    @field:Schema(description = "국가 코드", nullable = true) val country: String?,
) {
    constructor(member: Member) : this(
        id = member.id,
        handle = member.handle,
        nickname = member.nickname,
        imageUrl = member.imageUrl,
        country = member.country,
    )
}

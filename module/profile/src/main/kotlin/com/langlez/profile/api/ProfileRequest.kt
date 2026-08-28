package com.langlez.profile.api

import com.langlez.profile.domain.Profile
import jakarta.validation.constraints.Size

class ProfileRequest {
    /** presign 으로 받은 key. 조회용 URL 은 서버가 스토리지 확인 후 만든다. */
    data class ImageConfirm(val key: String)

    /** 이미 저장된 이미지를 가리킨다(대표 변경 등). 확정과 달리 조회용 URL 을 쓴다. */
    data class ImageSelect(val url: String)

    /** 성별·국가·생년월일은 계정(Member) 소유라 `PATCH /api/v1/members/me` 로 간다. */
    data class Update(
        @field:Size(max = 200, message = "validation.member.bio.size")
        val bio: String? = null,
        @field:Size(max = 500, message = "validation.member.goal.size")
        val goal: String? = null,
        @field:Size(max = 500, message = "validation.member.want.size")
        val want: String? = null,
        val mbti: Profile.MBTI? = null,
        val languageLevel: Profile.LanguageLevel? = null,
    )
}

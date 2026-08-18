package com.langlez.profile.api

import com.langlez.member.domain.Member
import com.langlez.profile.domain.Profile
import java.time.LocalDate
import java.util.*

class ProfileRequest {
    /** presign 으로 받은 key. 조회용 URL 은 서버가 스토리지 확인 후 만든다. */
    data class ImageConfirm(val key: String)

    /** 이미 저장된 이미지를 가리킨다(대표 변경 등). 확정과 달리 조회용 URL 을 쓴다. */
    data class ImageSelect(val url: String)

    data class Update(
        val bio: String? = null,
        val goal: String? = null,
        val want: String? = null,
        val gender: Member.Gender? = null,
        val mbti: Profile.MBTI? = null,
        val locale: Locale? = null,
        val birthDay: LocalDate? = null,
        val languageLevel: Profile.LanguageLevel? = null,
    )
}

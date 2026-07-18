package com.langlez.profile.api

import com.langlez.profile.domain.Profile
import java.time.LocalDate
import java.util.*

class ProfileRequest {
    data class ImageConfirm(val fileUrl: String)

    data class Update(
        val bio: String? = null,
        val goal: String? = null,
        val want: String? = null,
        val gender: Profile.Gender? = null,
        val mbti: Profile.MBTI? = null,
        val locale: Locale? = null,
        val birthDay: LocalDate? = null,
        val languageLevel: Profile.LanguageLevel? = null,
        val interests: Set<String>? = null,
    )
}

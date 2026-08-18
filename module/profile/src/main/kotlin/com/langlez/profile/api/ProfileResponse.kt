package com.langlez.profile.api

import com.langlez.member.domain.Member
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import java.time.Instant
import java.time.LocalDate
import java.util.*

class ProfileResponse {
    data class ProfileDetail(
        val bio: String?,
        val goal: String?,
        val want: String?,
        val gender: String,
        val mbti: String?,
        val locale: Locale?,
        val birthDay: LocalDate?,
        val languageLevel: String? = null,
        val interests: Set<String> = emptySet(),
    ) {
        constructor(profile: Profile, interests: Set<String> = emptySet()) : this(
            bio = profile.bio,
            goal = profile.goal,
            want = profile.want,
            // 성별/국가/생년월일은 계정(Member) 소유다.
            gender = profile.member.gender.name,
            mbti = profile.mbti?.name,
            locale = profile.member.locale,
            birthDay = profile.member.birthDay,
            languageLevel = profile.languageLevel?.name,
            interests = interests,
        )
    }

    data class Detail(
        val handle: String,
        val bio: String?,
        val goal: String?,
        val want: String?,
        val gender: String,
        val mbti: String?,
        val locale: Locale?,
        val birthDay: LocalDate?,
        val visitCount: Long,
        val languageLevel: String? = null,
        val interests: Set<String> = emptySet(),
    ) {
        constructor(profile: Profile, member: Member, visitCount: Long, interests: Set<String> = emptySet()) : this(
            handle = member.handle,
            bio = profile.bio,
            goal = profile.goal,
            want = profile.want,
            gender = member.gender.name,
            mbti = profile.mbti?.name,
            locale = member.locale,
            birthDay = member.birthDay,
            visitCount = visitCount,
            languageLevel = profile.languageLevel?.name,
            interests = interests,
        )
    }

    data class Image(
        val url: String,
        val sequence: Long,
        val represent: Boolean,
        val createdAt: Instant,
    ) {
        constructor(image: ProfileImage) : this(
            url = image.url,
            sequence = image.sequence,
            represent = image.represent,
            createdAt = image.createdAt,
        )
    }
}

package com.langlez.profile.api

import com.langlez.member.domain.Member
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import java.time.Instant
import java.time.LocalDate
import java.util.*

class ProfileResponse {
    data class Detail(
        val username: String,
        val nickname: String,
        val bio: String?,
        val goal: String?,
        val want: String?,
        val gender: String,
        val mbti: String?,
        val locale: Locale?,
        val birthDay: LocalDate?,
        val visitCount: Long,
    ) {
        constructor(profile: Profile, member: Member, visitCount: Long) : this(
            username = member.username,
            nickname = member.nickname,
            bio = profile.bio,
            goal = profile.goal,
            want = profile.want,
            gender = profile.gender.name,
            mbti = profile.mbti?.name,
            locale = profile.locale,
            birthDay = profile.birthDay,
            visitCount = visitCount,
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

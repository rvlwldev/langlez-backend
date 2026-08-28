package com.langlez.profile.api

import com.langlez.core.FollowQuery
import com.langlez.core.MemberQuery
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
        // 성별/국가/생년월일은 계정(Member) 소유라 core 포트로 받아 온다. 화면 하나에 요청이 둘이 되지 않게
        // 여기 함께 실어 보내지만, 수정은 members 엔드포인트로만 한다.
        constructor(
            profile: Profile,
            member: MemberQuery.ProfileInfo,
            interests: Set<String> = emptySet(),
        ) : this(
            bio = profile.bio,
            goal = profile.goal,
            want = profile.want,
            gender = member.gender,
            mbti = profile.mbti?.name,
            locale = member.locale,
            birthDay = member.birthDay,
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
        val followerCount: Long,
        val followingCount: Long,
        val languageLevel: String? = null,
        val interests: Set<String> = emptySet(),
    ) {
        // 두 숫자를 Long 두 개로 받으면 순서를 바꿔 넘겨도 컴파일된다. 묶어서 받는다.
        constructor(
            profile: Profile,
            member: MemberQuery.ProfileInfo,
            visitCount: Long,
            follows: FollowQuery.Counts,
            interests: Set<String> = emptySet(),
        ) : this(
            handle = member.handle,
            bio = profile.bio,
            goal = profile.goal,
            want = profile.want,
            gender = member.gender,
            mbti = profile.mbti?.name,
            locale = member.locale,
            birthDay = member.birthDay,
            visitCount = visitCount,
            followerCount = follows.followers,
            followingCount = follows.followings,
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

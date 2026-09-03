package com.langlez.matching.api.response

import com.langlez.lang.contract.LanguageReader
import com.langlez.matching.application.MatchScorer
import com.langlez.member.contract.MemberReader
import io.swagger.v3.oas.annotations.media.Schema

data class MatchingMemberResponse(
    @field:Schema(description = "회원 id") val id: Long,
    @field:Schema(description = "핸들") val handle: String,
    @field:Schema(description = "닉네임", nullable = true) val nickname: String?,
    @field:Schema(description = "대표 프로필 이미지 URL", nullable = true) val imageUrl: String?,

    @field:Schema(description = "요청 시점의 접속 여부. 순위와 달리 캐시하지 않고 매 요청 다시 읽는다.")
    val online: Boolean,

    @field:Schema(description = "상대의 언어 프로필")
    val languages: List<Language>,

    @field:Schema(description = "이 사람이 추천된 근거. 내 학습언어 ↔ 상대 모국어가 맞은 쌍이다.")
    val matchedPairs: List<MatchedPair>,
) {
    constructor(
        member: MemberReader.ProfileInfo,
        online: Boolean,
        languages: List<LanguageReader.LanguageInfo>,
        matchedPairs: List<MatchScorer.MatchedPair>,
    ) : this(
        id = member.id,
        handle = member.handle,
        nickname = member.nickname,
        imageUrl = member.imageUrl,
        online = online,
        languages = languages.map(::Language),
        matchedPairs = matchedPairs.map(::MatchedPair),
    )

    data class Language(
        @field:Schema(description = "BCP-47 언어 코드", example = "ko") val language: String,
        @field:Schema(description = "NATIVE / LEARNING") val role: String,
        @field:Schema(description = "학습언어일 때만 값이 있다.", nullable = true) val level: String?,
    ) {
        constructor(info: LanguageReader.LanguageInfo) : this(
            language = info.language,
            role = info.role.name,
            level = info.level?.name,
        )
    }

    data class MatchedPair(
        @field:Schema(description = "내가 배우는 언어") val myLearning: String,
        @field:Schema(description = "상대가 모국어로 하는 언어") val theirNative: String,
    ) {
        constructor(pair: MatchScorer.MatchedPair) : this(
            myLearning = pair.myLearning,
            theirNative = pair.theirNative,
        )
    }
}

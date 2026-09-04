package com.langlez.matching.api.response

import io.swagger.v3.oas.annotations.media.Schema

data class MatchingMembersResponse(
    @field:Schema(description = "추천 회원 한 페이지")
    val members: List<MatchingMemberResponse>,

    @field:Schema(description = "`offset + size` 가 캐시된 후보 수보다 작으면 true")
    val hasNext: Boolean,

    /**
     * 언어 미등록처럼 **에러는 아니지만 화면에 문구가 필요한** 경우에만 채운다.
     *
     * i18n 키가 아니라 요청 로케일로 이미 번역된 문장이다. 키를 그대로 내보내면
     * 클라이언트가 번역표를 따로 들어야 하고, 빠뜨리면 키 문자열이 사용자에게 노출된다.
     */
    @field:Schema(description = "안내 문구. 정상 응답이면 null", nullable = true)
    val message: String? = null,
)

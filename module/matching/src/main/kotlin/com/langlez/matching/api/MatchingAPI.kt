package com.langlez.matching.api

import com.langlez.matching.api.response.MatchingMembersResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import java.util.Locale

@Tag(name = "Matching", description = "언어 상호보완 추천 API")
interface MatchingAPI {

    @Operation(
        summary = "추천 회원 목록",
        description = "내가 배우는 언어를 모국어로 하고 동시에 내 모국어를 배우는 회원을 점수순으로 준다. " +
            "차단·이미 팔로우한 사람·정지·탈퇴 회원은 빠진다. " +
            "**커서가 아니라 offset 페이징이다** — 점수에 접속 상태가 섞여 커서 기준값이 다음 요청에 이미 달라진다. " +
            "순서는 10분 캐시되고 접속 표시만 매 요청 갱신된다. " +
            "언어를 등록하지 않았으면 빈 목록과 안내 문구가 온다(에러 아니다). 후보가 없어도 빈 목록이다.",
    )
    fun getMatches(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "건너뛸 개수") offset: Int,
        @Parameter(description = "true 면 캐시를 버리고 다시 뽑는다(당겨서 새로고침)") refresh: Boolean,
        locale: Locale,
    ): MatchingMembersResponse
}

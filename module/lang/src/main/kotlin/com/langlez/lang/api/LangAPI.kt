package com.langlez.lang.api

import com.langlez.lang.api.request.LangReplaceLanguagesRequest
import com.langlez.lang.api.response.LangMeResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Lang", description = "언어 프로필 API")
interface LangAPI {

    @Operation(summary = "내 언어 프로필 조회")
    fun getMyLanguages(memberId: Long): LangMeResponse

    @Operation(
        summary = "내 언어 프로필 전체 교체",
        description = "PATCH 가 아니라 PUT 이다. **보낸 목록이 곧 최종 상태**이고 빠진 언어는 삭제된다. " +
            "모국어는 최대 3개, 학습언어는 최대 5개다. 학습언어에는 레벨이 반드시 있어야 하고, " +
            "모국어에는 레벨을 실으면 안 된다. 같은 언어를 모국어와 학습언어로 동시에 등록할 수 없다.",
    )
    fun replaceMyLanguages(memberId: Long, request: LangReplaceLanguagesRequest): LangMeResponse
}

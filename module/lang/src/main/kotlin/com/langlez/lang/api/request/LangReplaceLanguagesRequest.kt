package com.langlez.lang.api.request

import com.langlez.lang.domain.MemberLanguage
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank

/**
 * 언어 프로필 **전체 교체** 요청. 보낸 것이 곧 최종 상태이고, 빠진 것은 삭제된다.
 *
 * 개별 추가·삭제로 쪼개지 않은 이유는 [com.langlez.lang.application.LanguageService.replace] KDoc 에 있다.
 */
data class LangReplaceLanguagesRequest(
    @field:Valid
    @field:Schema(description = "이 회원의 언어 전체 목록. 빈 배열이면 전부 삭제된다.")
    val languages: List<Item> = emptyList(),
) {
    data class Item(
        @field:NotBlank
        @field:Schema(description = "BCP-47 언어 코드", example = "zh-CN")
        val language: String,

        @field:Schema(description = "모국어인지 학습언어인지")
        val role: MemberLanguage.Role,

        @field:Schema(description = "학습언어일 때만 보낸다. 모국어에 레벨을 실으면 거부된다.", nullable = true)
        val level: MemberLanguage.Level? = null,
    )
}

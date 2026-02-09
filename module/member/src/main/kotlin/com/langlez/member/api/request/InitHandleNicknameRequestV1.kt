package com.langlez.member.api.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

/** Handle과 닉네임 초기화 요청 */
data class InitHandleNicknameRequestV1(
        @field:NotBlank @field:Size(min = 3, max = 20) val handle: String,
        @field:NotBlank @field:Size(min = 1, max = 50) val nickname: String
)

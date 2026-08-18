package com.langlez.member.api.request

import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.NotBlank

data class MemberUpdateImageRequest(
    @field:Schema(description = "presign으로 발급받아 업로드 완료한 첨부파일 key", example = "member/2026-08-03/uuid_photo.jpg")
    @field:NotBlank
    val key: String,
)

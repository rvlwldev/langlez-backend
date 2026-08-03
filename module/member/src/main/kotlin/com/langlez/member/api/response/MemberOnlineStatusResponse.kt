package com.langlez.member.api.response

import io.swagger.v3.oas.annotations.media.Schema

data class MemberOnlineStatusResponse(
    @field:Schema(description = "온라인 여부") val online: Boolean,
)

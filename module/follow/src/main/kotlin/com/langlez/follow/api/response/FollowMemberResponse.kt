package com.langlez.follow.api.response

import com.langlez.follow.application.FollowMemberView
import io.swagger.v3.oas.annotations.media.Schema

data class FollowMemberResponse(
    @field:Schema(description = "회원 id") val memberId: Long,
    @field:Schema(description = "handle(고유 아이디)") val handle: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val imageUrl: String?,
    @field:Schema(description = "다음 페이지 요청에 넣을 커서") val cursor: Long,
) {
    constructor(view: FollowMemberView) : this(
        memberId = view.memberId,
        handle = view.handle,
        imageUrl = view.imageUrl,
        cursor = view.cursor,
    )
}

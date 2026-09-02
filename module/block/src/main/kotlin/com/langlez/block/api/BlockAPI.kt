package com.langlez.block.api

import com.langlez.block.api.response.BlockMemberResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Block", description = "차단 API")
interface BlockAPI {

    @Operation(
        summary = "차단",
        description = "차단하면 서로의 팔로우 관계가 양방향으로 해제된다. " +
            "해제는 이벤트로 이뤄져 수 초 늦을 수 있으나, 차단 자체는 즉시 적용된다.",
    )
    fun block(memberId: Long, @Parameter(description = "차단할 회원 id") targetId: Long)

    @Operation(summary = "차단 해제", description = "차단 중이 아니어도 성공으로 끝난다. 팔로우는 복구되지 않는다.")
    fun unblock(memberId: Long, @Parameter(description = "차단 해제할 회원 id") targetId: Long)

    @Operation(
        summary = "내가 차단한 사람 목록",
        description = "최신순. cursor 는 직전 페이지 마지막 항목의 cursor 값을 넣는다.",
    )
    fun listBlocks(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 항목의 cursor") cursor: Long?,
    ): List<BlockMemberResponse>
}

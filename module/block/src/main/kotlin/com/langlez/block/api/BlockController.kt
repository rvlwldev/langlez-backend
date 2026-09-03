package com.langlez.block.api

import com.langlez.annotation.MemberId
import com.langlez.block.api.response.BlockMemberResponse
import com.langlez.block.application.BlockService
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/blocks")
class BlockController(private val service: BlockService) : BlockAPI {

    // `/me` 는 리터럴이라 `{targetId}` 패턴보다 먼저 매칭된다. 경로가 겹치지 않는다.
    @PostMapping("/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun block(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.block(memberId, targetId)
    }

    @DeleteMapping("/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun unblock(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.unblock(memberId, targetId)
    }

    @GetMapping("/me")
    override fun listBlocks(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<BlockMemberResponse> =
        service.listBlocks(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::BlockMemberResponse)

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 차단 목록을 통째로 긁어갈 수 있다.
        private const val MAX_SIZE = 50
    }
}

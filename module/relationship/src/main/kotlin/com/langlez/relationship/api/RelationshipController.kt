package com.langlez.relationship.api

import com.langlez.annotation.MemberId
import com.langlez.relationship.api.request.RelationshipReportRequest
import com.langlez.relationship.api.response.RelationshipMemberResponse
import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.domain.Report
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/relationships")
class RelationshipController(private val service: RelationshipService) : RelationshipAPI {

    @PostMapping("/follows/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun follow(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.follow(memberId, targetId)
    }

    @DeleteMapping("/follows/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun unfollow(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.unfollow(memberId, targetId)
    }

    @GetMapping("/me/followers")
    override fun listFollowers(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<RelationshipMemberResponse> =
        service.listFollowers(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::RelationshipMemberResponse)

    @GetMapping("/me/followings")
    override fun listFollowings(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<RelationshipMemberResponse> =
        service.listFollowings(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::RelationshipMemberResponse)

    @PostMapping("/blocks/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun block(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.block(memberId, targetId)
    }

    @DeleteMapping("/blocks/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun unblock(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.unblock(memberId, targetId)
    }

    @GetMapping("/me/blocks")
    override fun listBlocks(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<RelationshipMemberResponse> =
        service.listBlocks(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::RelationshipMemberResponse)

    @PostMapping("/reports")
    @ResponseStatus(NO_CONTENT)
    override fun report(@MemberId memberId: Long, @RequestBody @Valid request: RelationshipReportRequest) {
        service.report(
            reporterId = memberId,
            reportedUserId = request.authorId,
            sourceType = Report.SourceType.ECHO_POST,
            sourceId = request.postId.toString(),
            reason = request.reason,
        )
    }

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 팔로우 그래프를 통째로 긁어갈 수 있다.
        private const val MAX_SIZE = 50
    }
}

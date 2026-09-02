package com.langlez.follow.api

import com.langlez.annotation.MemberId
import com.langlez.follow.api.response.FollowMemberResponse
import com.langlez.follow.application.FollowService
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
@RequestMapping("/api/v1/follows")
class FollowController(private val service: FollowService) : FollowAPI {

    @PostMapping("/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun follow(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.follow(memberId, targetId)
    }

    @DeleteMapping("/{targetId}")
    @ResponseStatus(NO_CONTENT)
    override fun unfollow(@MemberId memberId: Long, @PathVariable targetId: Long) {
        service.unfollow(memberId, targetId)
    }

    @GetMapping("/me/followers")
    override fun listFollowers(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<FollowMemberResponse> =
        service.listFollowers(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::FollowMemberResponse)

    @GetMapping("/me/followings")
    override fun listFollowings(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<FollowMemberResponse> =
        service.listFollowings(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::FollowMemberResponse)

    // `/me` 는 리터럴이라 `{memberId}` 패턴보다 먼저 매칭된다. 경로가 겹치지 않는다.
    @GetMapping("/{memberId}/followers")
    override fun listFollowersOf(
        @MemberId viewerId: Long,
        @PathVariable("memberId") targetId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<FollowMemberResponse> =
        service.listFollowersOf(viewerId, targetId, size.coerceIn(1, MAX_SIZE), cursor)
            .map(::FollowMemberResponse)

    @GetMapping("/{memberId}/followings")
    override fun listFollowingsOf(
        @MemberId viewerId: Long,
        @PathVariable("memberId") targetId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<FollowMemberResponse> =
        service.listFollowingsOf(viewerId, targetId, size.coerceIn(1, MAX_SIZE), cursor)
            .map(::FollowMemberResponse)

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 팔로우 그래프를 통째로 긁어갈 수 있다.
        private const val MAX_SIZE = 50
    }
}

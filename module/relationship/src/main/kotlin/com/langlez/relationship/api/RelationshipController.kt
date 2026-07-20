package com.langlez.relationship.api

import com.langlez.core.LanglezException
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.application.RelationshipService
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/relationship")
class RelationshipController(
    private val service: RelationshipService,
    private val memberRepo: MemberRepository
) {

    @PostMapping("/follow/@{followingUsername}")
    @ResponseStatus(HttpStatus.CREATED)
    fun follow(@MemberID followerId: Long, @PathVariable followingUsername: String) {
        val followingId = resolveUsername(followingUsername)
        service.follow(followerId, followingId)
    }

    @DeleteMapping("/follow/@{followingUsername}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unfollow(@MemberID followerId: Long, @PathVariable followingUsername: String) {
        val followingId = resolveUsername(followingUsername)
        service.unfollow(followerId, followingId)
    }

    @PostMapping("/block/@{blockedUsername}")
    @ResponseStatus(HttpStatus.CREATED)
    fun block(@MemberID blockerId: Long, @PathVariable blockedUsername: String) {
        val blockedId = resolveUsername(blockedUsername)
        service.block(blockerId, blockedId)
    }

    @DeleteMapping("/block/@{blockedUsername}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unblock(@MemberID blockerId: Long, @PathVariable blockedUsername: String) {
        val blockedId = resolveUsername(blockedUsername)
        service.unblock(blockerId, blockedId)
    }

    @GetMapping("/followings")
    fun getFollowings(
        @MemberID followerId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): RelationshipResponse.CursorList =
        RelationshipResponse.CursorList.from(service.getFollowings(followerId, cursor, size))

    @GetMapping("/followers")
    fun getFollowers(
        @MemberID followedId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): RelationshipResponse.CursorList =
        RelationshipResponse.CursorList.from(service.getFollowers(followedId, cursor, size))

    @GetMapping("/blocks")
    fun getBlocks(
        @MemberID blockerId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): RelationshipResponse.CursorList =
        RelationshipResponse.CursorList.from(service.getBlocks(blockerId, cursor, size))

    private fun resolveUsername(username: String): Long =
        memberRepo.findByUsername(username)?.id
            ?: throw LanglezException(404, "member.not-found")
}

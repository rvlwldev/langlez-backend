package com.langlez.relationship.api

import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/relationship")
class RelationshipController(private val service: RelationshipService, private val repo: RelationshipRepository) {

    @PostMapping("/follow/@{followingId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun postFollow(@MemberID followerId: Long, @PathVariable("followingId") followingId: Long) {
        service.follow(followerId, followingId)
    }

    @PostMapping("/block/@{blockedId}")
    fun postBlock(@MemberID followerId: Long, @PathVariable("blockedId") blockedId: Long) {
    }

    @GetMapping("/followers")
    fun getFollowerList(@MemberID id: Long) {
    }

    @GetMapping("/followings")
    fun getFollowingList() {
    }

    @GetMapping("/followers")
    fun getBlockingList() {
    }

    @DeleteMapping("/follow/@{followingId}")
    fun deleteFollow(followingId: String) {
    }

    @DeleteMapping("/block/@{blockedId}")
    fun deleteBlock(blockedId: String) {
    }

}
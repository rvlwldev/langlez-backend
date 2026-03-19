package com.langlez.relationship.application

import com.langlez.core.OutBoxEventPublisher
import com.langlez.exception.LanglezException
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class RelationshipService(
    private val repo: RelationshipRepository,
    private val publisher: OutBoxEventPublisher
) {

    fun follow(followerId: Long, followingId: Long) {
        if (followerId == followingId)
            throw LanglezException(BAD_REQUEST, "social.follow.self")
        if (repo.findBlock(followingId, followerId) != null)
            throw LanglezException(FORBIDDEN, "social.follow.blocked")
        if (repo.findBlock(followerId, followingId) != null)
            throw LanglezException(FORBIDDEN, "social.follow.blocked")

        val follow = repo.saveFollow(Follow(followerId, followingId))
        val event = RelationshipEvent.Follow(followerId, followingId)
        publisher.publish("RELATIONSHIP", follow.id.toString(), "MEMBER_FOLLOW", event)
    }

    fun unfollow(followerId: Long, followingId: Long) {
        val follow = repo.findFollow(followerId, followingId) ?: return

        repo.deleteFollow(followerId, followingId)

        val event = RelationshipEvent.Unfollow(followerId, followingId)
        publisher.publish("RELATIONSHIP", follow.id.toString(), "MEMBER_UNFOLLOW", event)
    }

    fun block(blockerId: Long, blockedId: Long) {
        val block = repo.saveBlock(Block(blockerId, blockedId))

        repo.deleteFollow(blockerId, blockedId)
        repo.deleteFollow(blockedId, blockerId)

        val event = RelationshipEvent.Block(blockerId, blockedId)
        publisher.publish("RELATIONSHIP", block.id.toString(), "MEMBER_BLOCK", event)
    }

    fun unblock(blockerId: Long, blockedId: Long) {
        val block = repo.findBlock(blockerId, blockedId) ?: return

        repo.deleteBlock(blockerId, blockedId)

        val event = RelationshipEvent.Unblock(blockerId, blockedId)
        publisher.publish("RELATIONSHIP", block.id.toString(), "MEMBER_UNBLOCK", event)
    }

}


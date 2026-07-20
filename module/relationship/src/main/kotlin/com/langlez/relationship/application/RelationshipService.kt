package com.langlez.relationship.application

import com.langlez.core.LanglezException
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxRepository
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class RelationshipService(
    private val repo: RelationshipRepository,
    private val memberRepo: MemberRepository,
    private val outbox: RelationshipOutBoxRepository,
) {

    fun follow(followerId: Long, followingId: Long) {
        if (followerId == followingId)
            throw LanglezException(400, "social.follow.self")
        if (repo.findBlock(followingId, followerId) != null)
            throw LanglezException(403, "social.follow.blocked")
        if (repo.findBlock(followerId, followingId) != null)
            throw LanglezException(403, "social.follow.blocked")

        val follow = repo.saveFollow(Follow(followerId, followingId))
        val event = RelationshipEvent.Follow(followerId, followingId)
        outbox.save("RELATIONSHIP", follow.id.toString(), "MEMBER_FOLLOW", event)
    }

    fun unfollow(followerId: Long, followingId: Long) {
        val follow = repo.findFollow(followerId, followingId) ?: return

        repo.deleteFollow(followerId, followingId)

        val event = RelationshipEvent.Unfollow(followerId, followingId)
        outbox.save("RELATIONSHIP", follow.id.toString(), "MEMBER_UNFOLLOW", event)
    }

    fun block(blockerId: Long, blockedId: Long) {
        val block = repo.saveBlock(Block(blockerId, blockedId))

        repo.deleteFollow(blockerId, blockedId)
        repo.deleteFollow(blockedId, blockerId)

        val event = RelationshipEvent.Block(blockerId, blockedId)
        outbox.save("RELATIONSHIP", block.id.toString(), "MEMBER_BLOCK", event)
    }

    fun unblock(blockerId: Long, blockedId: Long) {
        val block = repo.findBlock(blockerId, blockedId) ?: return

        repo.deleteBlock(blockerId, blockedId)

        val event = RelationshipEvent.Unblock(blockerId, blockedId)
        outbox.save("RELATIONSHIP", block.id.toString(), "MEMBER_UNBLOCK", event)
    }

    @Transactional(readOnly = true)
    fun getFollowings(followerId: Long, cursor: Long?, size: Int): RelationshipResult.CursorList {
        val follows = repo.findFollowings(followerId, cursor, size)
        val memberIds = follows.map { it.followedId }
        return buildCursorList(memberIds, follows.size == size, follows.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun getFollowers(followedId: Long, cursor: Long?, size: Int): RelationshipResult.CursorList {
        val follows = repo.findFollowers(followedId, cursor, size)
        val memberIds = follows.map { it.followerId }
        return buildCursorList(memberIds, follows.size == size, follows.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun getBlocks(blockerId: Long, cursor: Long?, size: Int): RelationshipResult.CursorList {
        val blocks = repo.findBlocks(blockerId, cursor, size)
        val memberIds = blocks.map { it.blockedId }
        return buildCursorList(memberIds, blocks.size == size, blocks.lastOrNull()?.id)
    }

    private fun buildCursorList(
        memberIds: List<Long>,
        hasMore: Boolean,
        lastEntityId: Long?,
    ): RelationshipResult.CursorList {
        val members = memberRepo.findByIds(memberIds)
        val memberMap = members.associateBy { it.id }
        val summaries = memberIds.mapNotNull { id -> memberMap[id] }
            .map { RelationshipResult.MemberSummary(it.username, it.nickname) }
        val nextCursor = if (hasMore) lastEntityId else null
        return RelationshipResult.CursorList(nextCursor, summaries)
    }
}

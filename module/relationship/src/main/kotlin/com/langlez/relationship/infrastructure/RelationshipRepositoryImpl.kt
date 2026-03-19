package com.langlez.relationship.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

interface FollowJpaRepository : JpaRepository<Follow, Long> {
    fun findByFollowerIdAndFollowedId(followerId: Long, followedId: Long): Follow?
    fun countByFollowerId(followerId: Long): Long
    fun countByFollowedId(followedId: Long): Long
}

interface BlockJpaRepository : JpaRepository<Block, Long>

@Repository
class RelationshipRepositoryImpl(
    private val followJpa: FollowJpaRepository,
    private val blockJpa: BlockJpaRepository,
    private val memberRepo: MemberRepository
) : RelationshipRepository {

    override fun saveFollow(follow: Follow): Follow =
        followJpa.save(follow)

    override fun findFollow(followerId: Long, followedId: Long): Follow? =
        followJpa.findByFollowerIdAndFollowedId(followerId, followedId)

    override fun findFollowingMembers(id: Long): List<Member> {
        TODO("Not yet implemented")
    }

    override fun findFollowerMembers(id: Long): List<Member> {
        TODO("Not yet implemented")
    }

    override fun countFollowings(id: Long): Long =
        followJpa.countByFollowerId(id)

    override fun countFollowers(id: Long): Long =
        followJpa.countByFollowedId(id)

    override fun deleteFollow(followerId: Long, followedId: Long) {
        TODO("Not yet implemented")
    }

    override fun saveBlock(block: Block): Block {
        TODO("Not yet implemented")
    }

    override fun findBlock(blockerId: Long, blockedId: Long): Block? {
        TODO("Not yet implemented")
    }

    override fun findBlockingMembers(blockerId: Long): List<Member> {
        TODO("Not yet implemented")
    }

    override fun deleteBlock(blockerId: Long, blockedId: Long) {
        TODO("Not yet implemented")
    }
}


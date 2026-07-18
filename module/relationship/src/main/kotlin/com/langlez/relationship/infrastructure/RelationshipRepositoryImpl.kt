package com.langlez.relationship.infrastructure

import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class RelationshipRepositoryImpl(
    private val followJpa: FollowJpaRepository,
    private val blockJpa: BlockJpaRepository,
) : RelationshipRepository {

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["follow"], key = "#follow.followerId + ':' + #follow.followedId"),
            CacheEvict(cacheNames = ["follow_count"], key = "'following:' + #follow.followerId"),
            CacheEvict(cacheNames = ["follow_count"], key = "'follower:' + #follow.followedId"),
        ]
    )
    override fun saveFollow(follow: Follow): Follow =
        followJpa.save(follow)

    @Cacheable(cacheNames = ["follow"], key = "#followerId + ':' + #followedId")
    override fun findFollow(followerId: Long, followedId: Long): Follow? =
        followJpa.findByFollowerIdAndFollowedId(followerId, followedId)

    override fun findFollowings(followerId: Long, cursor: Long?, size: Int): List<Follow> =
        followJpa.findFollowings(followerId, cursor, PageRequest.of(0, size))

    override fun findFollowers(followedId: Long, cursor: Long?, size: Int): List<Follow> =
        followJpa.findFollowers(followedId, cursor, PageRequest.of(0, size))

    @Cacheable(cacheNames = ["follow_count"], key = "'following:' + #followerId")
    override fun countFollowings(followerId: Long): Long =
        followJpa.countByFollowerId(followerId)

    @Cacheable(cacheNames = ["follow_count"], key = "'follower:' + #followedId")
    override fun countFollowers(followedId: Long): Long =
        followJpa.countByFollowedId(followedId)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["follow"], key = "#followerId + ':' + #followedId"),
            CacheEvict(cacheNames = ["follow_count"], key = "'following:' + #followerId"),
            CacheEvict(cacheNames = ["follow_count"], key = "'follower:' + #followedId"),
        ]
    )
    override fun deleteFollow(followerId: Long, followedId: Long) =
        followJpa.deleteByFollowerIdAndFollowedId(followerId, followedId)

    @Caching(
        evict = [
            CacheEvict(cacheNames = ["block"], key = "#block.blockerId + ':' + #block.blockedId"),
        ]
    )
    override fun saveBlock(block: Block): Block =
        blockJpa.save(block)

    @Cacheable(cacheNames = ["block"], key = "#blockerId + ':' + #blockedId")
    override fun findBlock(blockerId: Long, blockedId: Long): Block? =
        blockJpa.findByBlockerIdAndBlockedId(blockerId, blockedId)

    override fun findBlocks(blockerId: Long, cursor: Long?, size: Int): List<Block> =
        blockJpa.findBlocks(blockerId, cursor, PageRequest.of(0, size))

    @CacheEvict(cacheNames = ["block"], key = "#blockerId + ':' + #blockedId")
    override fun deleteBlock(blockerId: Long, blockedId: Long) =
        blockJpa.deleteByBlockerIdAndBlockedId(blockerId, blockedId)
}

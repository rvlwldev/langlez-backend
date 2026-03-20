package com.langlez.relationship.infrastructure

import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.cache.annotation.Caching
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

interface FollowJpaRepository : JpaRepository<Follow, Long> {
    fun findByFollowerIdAndFollowedId(followerId: Long, followedId: Long): Follow?
    fun countByFollowerId(followerId: Long): Long
    fun countByFollowedId(followedId: Long): Long
    fun deleteByFollowerIdAndFollowedId(followerId: Long, followedId: Long)

    @Query("SELECT f FROM Follow f WHERE f.followerId = :followerId AND (:cursor IS NULL OR f.id < :cursor) ORDER BY f.id DESC")
    fun findFollowings(followerId: Long, cursor: Long?, pageable: PageRequest): List<Follow>

    @Query("SELECT f FROM Follow f WHERE f.followedId = :followedId AND (:cursor IS NULL OR f.id < :cursor) ORDER BY f.id DESC")
    fun findFollowers(followedId: Long, cursor: Long?, pageable: PageRequest): List<Follow>
}

interface BlockJpaRepository : JpaRepository<Block, Long> {
    fun findByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Block?
    fun deleteByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long)

    @Query("SELECT b FROM Block b WHERE b.blockerId = :blockerId AND (:cursor IS NULL OR b.id < :cursor) ORDER BY b.id DESC")
    fun findBlocks(blockerId: Long, cursor: Long?, pageable: PageRequest): List<Block>
}

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

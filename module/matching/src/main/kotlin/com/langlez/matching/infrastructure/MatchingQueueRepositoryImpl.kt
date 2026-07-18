package com.langlez.matching.infrastructure

import com.langlez.matching.domain.MatchingQueueRepository
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class MatchingQueueRepositoryImpl(
    private val redissonClient: RedissonClient,
) : MatchingQueueRepository {

    private fun queue(): RScoredSortedSet<Long> = redissonClient.getScoredSortedSet(QUEUE_KEY)

    override fun isQueued(memberId: Long): Boolean = queue().contains(memberId)

    override fun add(memberId: Long, score: Double) {
        queue().add(score, memberId)
    }

    override fun remove(memberId: Long): Boolean = queue().remove(memberId)

    override fun score(memberId: Long): Double? = queue().getScore(memberId)

    override fun candidatesInRange(min: Double, max: Double): List<Long> =
        queue().valueRange(min, true, max, true).toList()

    override fun allMembers(): List<Long> = queue().readAll().toList()

    override fun saveJoinedAt(memberId: Long, at: Instant) {
        redissonClient.getBucket<Long>(joinedAtKey(memberId)).set(at.toEpochMilli(), JOINED_AT_TTL)
    }

    override fun findJoinedAt(memberId: Long): Instant? =
        redissonClient.getBucket<Long>(joinedAtKey(memberId)).get()?.let { Instant.ofEpochMilli(it) }

    override fun removeJoinedAt(memberId: Long) {
        redissonClient.getBucket<Long>(joinedAtKey(memberId)).delete()
    }

    companion object {
        const val QUEUE_KEY = "matching:queue"
        private val JOINED_AT_TTL: Duration = Duration.ofMinutes(5)
        private fun joinedAtKey(memberId: Long) = "matching:joined-at:$memberId"
    }
}

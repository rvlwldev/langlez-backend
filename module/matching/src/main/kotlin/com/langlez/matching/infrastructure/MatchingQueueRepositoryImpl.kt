package com.langlez.matching.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.matching.domain.MatchingQueueFilter
import com.langlez.matching.domain.MatchingQueueRepository
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class MatchingQueueRepositoryImpl(
    private val redissonClient: RedissonClient,
    private val objectMapper: ObjectMapper,
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
        redissonClient.getBucket<Long>(joinedAtKey(memberId)).set(at.toEpochMilli())
    }

    override fun findJoinedAt(memberId: Long): Instant? =
        redissonClient.getBucket<Long>(joinedAtKey(memberId)).get()?.let { Instant.ofEpochMilli(it) }

    override fun removeJoinedAt(memberId: Long) {
        redissonClient.getBucket<Long>(joinedAtKey(memberId)).delete()
    }

    override fun saveFilter(memberId: Long, filter: MatchingQueueFilter) {
        val json = objectMapper.writeValueAsString(filter)
        redissonClient.getBucket<String>(filterKey(memberId)).set(json)
    }

    override fun findFilter(memberId: Long): MatchingQueueFilter? {
        val json = redissonClient.getBucket<String>(filterKey(memberId)).get() ?: return null
        return objectMapper.readValue(json, MatchingQueueFilter::class.java)
    }

    override fun removeFilter(memberId: Long) {
        redissonClient.getBucket<String>(filterKey(memberId)).delete()
    }

    companion object {
        const val QUEUE_KEY = "matching:queue"
        private fun joinedAtKey(memberId: Long) = "matching:joined-at:$memberId"
        private fun filterKey(memberId: Long) = "matching:filter:$memberId"
    }
}


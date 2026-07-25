package com.langlez.matching.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.matching.domain.MatchingQueueFilter
import com.langlez.matching.domain.MatchingQueueRepository
import com.langlez.matching.domain.QueueMemberMeta
import org.redisson.api.RMap
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
    private fun metaMap(): RMap<String, String> = redissonClient.getMap(META_KEY)

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
        val current = getMeta(memberId) ?: QueueMemberMeta(joinedAt = at)
        val updated = current.copy(joinedAt = at)
        saveMeta(memberId, updated)
    }

    override fun findJoinedAt(memberId: Long): Instant? = getMeta(memberId)?.joinedAt

    override fun removeJoinedAt(memberId: Long) {
        val current = getMeta(memberId) ?: return
        if (current.filter == null) {
            metaMap().fastRemove(memberId.toString())
        } else {
            saveMeta(memberId, current.copy(joinedAt = Instant.EPOCH))
        }
    }

    override fun saveFilter(memberId: Long, filter: MatchingQueueFilter) {
        val current = getMeta(memberId) ?: QueueMemberMeta(joinedAt = Instant.now())
        val updated = current.copy(filter = filter)
        saveMeta(memberId, updated)
    }

    override fun findFilter(memberId: Long): MatchingQueueFilter? = getMeta(memberId)?.filter

    override fun removeFilter(memberId: Long) {
        metaMap().fastRemove(memberId.toString())
    }

    override fun findMetaInBatch(memberIds: List<Long>): Map<Long, QueueMemberMeta> {
        if (memberIds.isEmpty()) return emptyMap()
        val rawMap = metaMap().getAll(memberIds.map { it.toString() }.toSet())
        val result = mutableMapOf<Long, QueueMemberMeta>()
        for ((keyStr, json) in rawMap) {
            if (json != null) {
                val id = keyStr.toLongOrNull() ?: continue
                try {
                    result[id] = objectMapper.readValue(json, QueueMemberMeta::class.java)
                } catch (_: Exception) {}
            }
        }
        return result
    }

    private fun getMeta(memberId: Long): QueueMemberMeta? {
        val json = metaMap().get(memberId.toString()) ?: return null
        return try {
            objectMapper.readValue(json, QueueMemberMeta::class.java)
        } catch (_: Exception) {
            null
        }
    }

    private fun saveMeta(memberId: Long, meta: QueueMemberMeta) {
        val json = objectMapper.writeValueAsString(meta)
        metaMap().fastPut(memberId.toString(), json)
    }

    companion object {
        const val QUEUE_KEY = "matching:queue"
        const val META_KEY = "matching:queue:meta"
    }
}



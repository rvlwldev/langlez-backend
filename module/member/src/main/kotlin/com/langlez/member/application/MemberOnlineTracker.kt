package com.langlez.member.application

import com.langlez.member.domain.MemberRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.redisson.api.RedissonClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class MemberOnlineTracker(private val redisson: RedissonClient, private val repo: MemberRepository) {

    fun toOnline(handle: String) {
        redisson.getBucket<String>(key(handle)).set("1", Duration.ofMinutes(TTL))
        redisson.getScoredSortedSet<String>(ZSET_KEY).add(now(), handle)
    }

    fun toOffline(handle: String) {
        redisson.getBucket<String>(key(handle)).delete()
        redisson.getScoredSortedSet<String>(ZSET_KEY).remove(handle)
    }

    fun checkStatus(handle: String): Boolean? {
        val isOnline = redisson.getBucket<String>(key(handle)).isExists
        if (isOnline) return true

        repo.find(handle) ?: return null
        return false
    }

    fun checkStatus(handles: Collection<String>): Map<String, Boolean?> {
        if (handles.isEmpty()) return emptyMap()

        val targets = handles.toSet()
        val keymap = targets.associateBy { key(it) } // key to handle

        val buckets = redisson.buckets.get<String>(*keymap.keys.toTypedArray())
        val onlineMap = keymap.entries.associate { (key, handle) -> handle to (buckets[key] != null) }

        val offlines = onlineMap.filterValues { !it }.keys.toList()
        val presences = repo.findAllByHandles(offlines).map { it.handle }

        return targets.associateWith { handle ->
            when {
                onlineMap[handle] == true -> true
                presences.contains(handle) -> false
                else -> null
            }
        }
    }

    fun countOnline(): Long = redisson.getScoredSortedSet<String>(ZSET_KEY).size().toLong()

    @Scheduled(cron = "0 */5 * * * *")
    @DistributedLock(prefix = "lock:clear-offlines")
    fun clearOfflines() {
        val cutoff = (now() - Duration.ofMinutes(TTL).toMillis())
        redisson.getScoredSortedSet<String>(ZSET_KEY).removeRangeByScore(0.0, true, cutoff, false)
    }

    @Scheduled(cron = "0 */15 * * * *")
    @DistributedLock(transactional = true, prefix = "lock:update-access-at")
    fun updateAccessedAt() {
        val cutoff = now() - Duration.ofMinutes(SYNC_INTERVAL_MINUTES).toMillis()
        val entries = redisson.getScoredSortedSet<String>(ZSET_KEY)
            .entryRange(cutoff, true, now(), true)

        if (entries.isEmpty()) return

        val accessedAtByHandle = entries.associate { it.value to Instant.ofEpochMilli(it.score.toLong()) }
        val members = repo.findAllByHandles(accessedAtByHandle.keys.toList())

        members.forEach { member ->
            val accessedAt = accessedAtByHandle[member.handle] ?: return@forEach
            member.updateAccessedAt(accessedAt)
            repo.save(member)
        }
    }

    private fun key(handle: String): String = "online:$handle"
    private fun now() = System.currentTimeMillis().toDouble()

    companion object {
        private const val ZSET_KEY = "member:online"
        private const val TTL = 15L
        private const val SYNC_INTERVAL_MINUTES = 15L
    }
}

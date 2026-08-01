package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.redisson.api.RedissonClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.Instant

@Component
class MemberOnlineTracker(private val redisson: RedissonClient, private val repo: MemberRepository) {

    fun toOnline(username: String) {
        redisson.getBucket<String>(key(username)).set("1", Duration.ofMinutes(TTL))
        redisson.getScoredSortedSet<String>(ZSET_KEY).add(now(), username)
    }

    fun toOffline(username: String) {
        redisson.getBucket<String>(key(username)).delete()
        redisson.getScoredSortedSet<String>(ZSET_KEY).remove(username)
    }

    fun checkStatus(username: String): Boolean? {
        val isOnline = redisson.getBucket<String>(key(username)).isExists
        if (isOnline) return true

        repo.findByUsername(username) ?: return null
        return false
    }

    fun checkStatus(usernames: Collection<String>): Map<String, Boolean?> {
        if (usernames.isEmpty()) return emptyMap()

        val targets = usernames.toSet()
        val keymap = targets.associateBy { key(it) } // key to username

        val buckets = redisson.buckets.get<String>(*keymap.keys.toTypedArray())
        val onlineMap = keymap.entries.associate { (key, username) -> username to (buckets[key] != null) }

        val offlines = onlineMap.filterValues { !it }.keys.toList()
        val presences = repo.findByUsernames(offlines).map { it.username }

        return targets.associateWith { username ->
            when {
                onlineMap[username] == true -> true
                presences.contains(username) -> false
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

        val accessedAtByUsername = entries.associate { it.value to Instant.ofEpochMilli(it.score.toLong()) }
        val members = repo.findByUsernames(accessedAtByUsername.keys.toList())

        members.forEach { member ->
            val accessedAt = accessedAtByUsername[member.username] ?: return@forEach
            member.updateAccessedAt(accessedAt)
            repo.save(member)
        }
    }

    private fun key(username: String): String = "online:$username"
    private fun now() = System.currentTimeMillis().toDouble()

    companion object {
        private const val ZSET_KEY = "member:online"
        private const val TTL = 15L
        private const val SYNC_INTERVAL_MINUTES = 15L
    }
}

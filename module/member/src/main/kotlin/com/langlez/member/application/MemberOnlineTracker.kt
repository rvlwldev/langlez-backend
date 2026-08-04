package com.langlez.member.application

import com.langlez.core.OnlineTracker
import com.langlez.member.domain.MemberRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.redisson.api.RedissonClient
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

@Component
class MemberOnlineTracker(
    private val redisson: RedissonClient,
    private val repo: MemberRepository,
) : OnlineTracker {

    override fun toOnline(id: Long) {
        redisson.getBucket<String>(key(id)).set("1", TTL)
        redisson.getScoredSortedSet<String>(PING_ZSET_KEY).add(now(), id.toString())
    }

    override fun toOffline(id: Long) {
        redisson.getBucket<String>(key(id)).delete()
        redisson.getScoredSortedSet<String>(PING_ZSET_KEY).remove(id.toString())
    }

    override fun checkOnline(id: Long): Map<Long, Boolean> = checkOnline(listOf(id))

    // 레디스에 키가 없으면(TTL 만료 포함) 그냥 오프라인이다. DB는 안 본다.
    override fun checkOnline(id: Collection<Long>): Map<Long, Boolean> {
        if (id.isEmpty()) return emptyMap()

        val targets = id.toSet()
        val keymap = targets.associateBy { key(it) } // bucket key -> member id

        val buckets = redisson.buckets.get<String>(*keymap.keys.toTypedArray())
        return keymap.entries.associate { (bucketKey, memberId) -> memberId to (buckets[bucketKey] != null) }
    }

    // 개별 키 TTL 만료 여부를 셀 방법이 없어(SCAN은 못 씀), 같은 핑을 스코어=시각으로 한 번 더 ZSET에 남겨
    // 최근 TTL 이내 스코어 개수를 센다. toOffline이 즉시 지워주니 로그아웃 반영도 바로 된다.
    override fun countOnline(): Long {
        val cutoff = now() - TTL.toMillis()
        return redisson.getScoredSortedSet<String>(PING_ZSET_KEY)
            .count(cutoff, true, now(), true).toLong()
    }

    /**
     * OnlineTracker 인터페이스엔 없는 member 전용 부가기능. 접속 핑마다 DB를 치면 감당이
     * 안 되니, 위 ZSET에 쌓인 핑을 모아 주기적으로만 lastAccessedAt에 반영한다.
     * 처리한 구간은 지워서 ZSET이 무한정 늘어나지 않게 한다(예전 clearOfflines가 하던 청소 역할을 겸함).
     */
    @Scheduled(cron = "0 */10 * * * *")
    @DistributedLock(transactional = true, prefix = "lock:update-access-at")
    fun updateAccessedAt() {
        val end = now()
        val start = end - Duration.ofMinutes(SYNC_INTERVAL_MINUTES).toMillis()
        val zset = redisson.getScoredSortedSet<String>(PING_ZSET_KEY)
        val entries = zset.entryRange(start, true, end, true)

        if (entries.isEmpty()) return

        val accessedAtById = entries.associate { it.value.toLong() to Instant.ofEpochMilli(it.score.toLong()) }
        val members = repo.findAll(accessedAtById.keys)

        members.forEach { member ->
            val accessedAt = accessedAtById[member.id] ?: return@forEach
            member.updateAccessedAt(accessedAt)
            repo.save(member)
        }

        zset.removeRangeByScore(start, true, end, true)
    }

    private fun key(id: Long): String = "online:$id"
    private fun now() = System.currentTimeMillis().toDouble()

    companion object {
        private const val PING_ZSET_KEY = "member:online-pings"
        private val TTL: Duration = Duration.ofSeconds(10)
        private const val SYNC_INTERVAL_MINUTES = 10L
    }
}

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
        redisson.getScoredSortedSet<Long>(PING_ZSET_KEY).add(now(), id)
        refreshViewing(id)
    }

    override fun toOffline(id: Long) {
        redisson.getBucket<String>(key(id)).delete()
        redisson.getScoredSortedSet<Long>(PING_ZSET_KEY).remove(id)
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
        return redisson.getScoredSortedSet<Long>(PING_ZSET_KEY)
            .count(cutoff, true, now(), true).toLong()
    }

    /**
     * 화면 상태. `viewing:{topic}` 이 그 방을 보고 있는 회원들, `viewing:member:{id}` 가 그 역인덱스다.
     * 역인덱스가 없으면 DISCONNECT 때 이 회원이 어떤 방을 보고 있었는지 찾을 방법이 SCAN 뿐인데,
     * SCAN 은 키 공간 전체를 훑어 운영 레디스를 세운다(핑 ZSET 을 따로 두는 이유와 같다).
     *
     * 회원 id 를 Long 이 아니라 문자열로 담는다. 공용 코덱(JsonJacksonCodec)은 final 타입에
     * 타입 정보를 안 붙이고 디코딩은 Object 로 해서, 작은 수가 Integer 로 되돌아온다.
     * 그러면 `Set<Long>` 인 척하는 Integer 집합이 되어 `contains(1L)` 이 조용히 false 가 된다.
     */
    override fun recordViewing(memberId: Long, topic: String) {
        viewersOf(topic).apply { add(memberId.toString()); expire(VIEWING_TTL) }
        topicsOf(memberId).apply { add(topic); expire(VIEWING_TTL) }
    }

    override fun clearViewing(memberId: Long, topic: String) {
        viewersOf(topic).remove(memberId.toString())
        topicsOf(memberId).remove(topic)
    }

    override fun clearAllViewing(memberId: Long) {
        val topics = topicsOf(memberId)
        topics.readAll().forEach { viewersOf(it).remove(memberId.toString()) }
        topics.delete()
    }

    /**
     * 그 방을 보고 있는 회원.
     *
     * 구독 기록만 믿으면 안 된다. 앱이 죽어 DISCONNECT 를 못 보낸 사용자가 남고,
     * 같은 방을 보는 다른 사람의 핑이 TTL 을 계속 갱신해 그 항목이 영원히 살아 있다.
     * 그러면 그 사람은 "보고 있으니 푸시 불필요"로 판정돼 알림을 영영 못 받는다.
     * 접속 여부(핑 TTL 1분)와 교집합을 취해 걸러낸다. 크래시로 DISCONNECT 를 못 보낸 클라이언트는
     * 이제 최대 1분간 "보는 중"으로 남아 그동안 채팅 알림이 조용히 안 간다(예전엔 이 창이 10초였다).
     */
    override fun viewers(topic: String): Set<Long> {
        val subscribed = viewersOf(topic).readAll().mapNotNull(String::toLongOrNull).toSet()
        if (subscribed.isEmpty()) return emptySet()

        val online = checkOnline(subscribed)
        return subscribed.filterTo(mutableSetOf()) { online[it] == true }
    }

    /**
     * 구독은 한 번뿐이라 TTL 을 구독 시점에만 걸면, 한 방을 TTL 보다 오래 보고 있는 사람이
     * 조용히 목록에서 빠져 "보고 있는 방"의 알림을 받게 된다. 핑마다 만료를 미뤄 그걸 막는다.
     * TTL 자체는 인스턴스가 죽어 DISCONNECT 를 못 받았을 때 영원히 "보는 중"으로 남는 걸 막는 안전장치다.
     *
     * TTL 이 키 단위라 죽은 사용자의 항목이 남을 수 있지만, `viewers` 가 접속 여부와
     * 교집합을 취하므로 판정에는 영향이 없다.
     */
    private fun refreshViewing(memberId: Long) {
        val topics = topicsOf(memberId)
        val viewing = topics.readAll()
        if (viewing.isEmpty()) return

        viewing.forEach { viewersOf(it).expire(VIEWING_TTL) }
        topics.expire(VIEWING_TTL)
    }

    private fun viewersOf(topic: String) = redisson.getSet<String>("$VIEWING_PREFIX$topic")
    private fun topicsOf(memberId: Long) = redisson.getSet<String>("$VIEWING_MEMBER_PREFIX$memberId")

    /**
     * OnlineTracker 인터페이스엔 없는 member 전용 부가기능.
     * 로그인·토큰 갱신 때 마지막 접속 IP/기기를 남긴다. 최신 값만 덮어쓰므로
     * 같은 회원이 여러 번 접속해도 DB 쓰기는 아래 동기화에서 1회다.
     */
    fun recordAccess(id: Long, ip: String?, deviceId: String?) {
        if (ip == null && deviceId == null) return

        val map = redisson.getMap<String, String>(accessKey(id))
        ip?.let { map.put(FIELD_IP, it) }
        deviceId?.let { map.put(FIELD_DEVICE, it) }

        redisson.getSet<Long>(ACCESS_DIRTY_KEY).add(id)
    }

    /**
     * 접속 정보를 DB 에 반영한다. 접속 시각(핑 ZSET)과 IP/기기(해시)를 **한 번에** 처리한다.
     *
     * 접속마다 DB를 치면 감당이 안 되니 레디스에 모아뒀다가 주기적으로만 내린다.
     * 둘을 별도 스케줄러로 나누면 같은 `member.audit` 행을 두 트랜잭션이 각자 merge 해
     * 서로를 덮어쓰고 @Version 이 충돌한다. 그래서 하나의 락, 하나의 조회, 회원당 한 번의 저장으로 묶는다.
     * 처리한 구간/키는 지워서 레디스가 무한정 늘어나지 않게 한다.
     */
    @Scheduled(cron = "0 */10 * * * *")
    @DistributedLock(transactional = true, prefix = "lock:member-access-sync")
    fun syncAccessInfo() {
        val end = now()
        val start = end - Duration.ofMinutes(SYNC_INTERVAL_MINUTES).toMillis()

        val zset = redisson.getScoredSortedSet<Long>(PING_ZSET_KEY)
        val accessedAtById = zset.entryRange(start, true, end, true)
            .associate { it.value to Instant.ofEpochMilli(it.score.toLong()) }

        val dirty = redisson.getSet<Long>(ACCESS_DIRTY_KEY)
        val dirtyIds = dirty.readAll()

        val targets = accessedAtById.keys + dirtyIds
        if (targets.isEmpty()) return

        // 먼저 꺼낸다. 이 사이에 새로 들어온 기록은 다음 주기에 처리된다.
        if (dirtyIds.isNotEmpty()) dirty.removeAll(dirtyIds)

        repo.findAll(targets).forEach { member ->
            accessedAtById[member.id]?.let(member::updateAccessedAt)

            if (member.id in dirtyIds) {
                val meta = redisson.getMap<String, String>(accessKey(member.id)).readAllMap()
                meta[FIELD_IP]?.let { member.audit.lastAccessedIp = it }
                meta[FIELD_DEVICE]?.let { member.audit.lastDeviceId = it }
                redisson.getMap<String, String>(accessKey(member.id)).delete()
            }

            repo.save(member)
        }

        zset.removeRangeByScore(start, true, end, true)
    }

    private fun key(id: Long): String = "online:$id"
    private fun accessKey(id: Long): String = "$ACCESS_PREFIX$id"
    private fun now() = System.currentTimeMillis().toDouble()

    companion object {
        private const val PING_ZSET_KEY = "member:online-pings"
        private const val ACCESS_PREFIX = "member:access:"
        private const val ACCESS_DIRTY_KEY = "member:access:dirty"
        private const val FIELD_IP = "ip"
        private const val FIELD_DEVICE = "device"
        private const val VIEWING_PREFIX = "viewing:"
        private const val VIEWING_MEMBER_PREFIX = "viewing:member:"
        private val TTL: Duration = Duration.ofMinutes(1)
        private val VIEWING_TTL: Duration = Duration.ofMinutes(5)
        private const val SYNC_INTERVAL_MINUTES = 10L
    }
}

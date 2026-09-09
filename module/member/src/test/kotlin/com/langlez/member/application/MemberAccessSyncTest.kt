package com.langlez.member.application

import com.langlez.member.domain.MemberRepository
import com.langlez.redis.distributedLock.DistributedLock
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.redisson.api.RMap
import org.redisson.api.RScoredSortedSet
import org.redisson.api.RSet
import org.redisson.api.RedissonClient
import org.redisson.client.protocol.ScoredEntry
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

class MemberAccessSyncTest : BehaviorSpec({

    Given("한 회원이 10분 안에 핑도 보내고 로그인도 했으면") {
        val redisson = mockk<RedissonClient>(relaxed = true)
        val repo = mockk<MemberRepository>(relaxed = true)
        val tracker = MemberOnlineTracker(redisson, repo)

        val pingAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        val zset = mockk<RScoredSortedSet<Long>>(relaxed = true)
        every { redisson.getScoredSortedSet<Long>(any<String>()) } returns zset
        every { zset.entryRange(0.0, true, any(), true) } returns
            listOf(ScoredEntry(pingAt.toEpochMilli().toDouble(), 1L))

        val dirty = mockk<RSet<Long>>(relaxed = true)
        every { redisson.getSet<Long>(any<String>()) } returns dirty
        every { dirty.readAll() } returns setOf(1L)

        val accessMap = mockk<RMap<String, String>>(relaxed = true)
        every { redisson.getMap<String, String>(any<String>()) } returns accessMap
        every { accessMap.readAllMap() } returns mapOf("ip" to "1.2.3.4", "device" to "device-A")

        tracker.syncAccessInfo()

        Then("분산 락은 DB 트랜잭션 없이 획득한다") {
            val method = MemberOnlineTracker::class.java.getDeclaredMethod("syncAccessInfo")
            val lock = method.getAnnotation(DistributedLock::class.java)
            lock.transactional shouldBe false
        }

        Then("ZSET 은 0.0 부터 현재까지 정리해 과거 지연 핑 누수를 막는다") {
            verify(exactly = 1) { zset.removeRangeByScore(0.0, true, any(), true) }
        }

        Then("엔티티 save 가 아닌 updateAccessInfo 로 메타데이터만 갱신한다") {
            verify(exactly = 1) {
                repo.updateAccessInfo(
                    id = 1L,
                    accessedAt = pingAt,
                    ip = "1.2.3.4",
                    deviceId = "device-A",
                )
            }
            verify(exactly = 0) { repo.save(any()) }
            verify(exactly = 0) { repo.findAll(any<Collection<Long>>()) }
        }

        Then("레디스 dirty 상태와 access 해시맵이 삭제된다") {
            verify(exactly = 1) { dirty.removeAll(setOf(1L)) }
            verify(exactly = 1) { accessMap.delete() }
        }
    }

    Given("스케줄러 지연 등으로 10분보다 오래된 핑이 ZSET 에 남아있을 때") {
        val redisson = mockk<RedissonClient>(relaxed = true)
        val repo = mockk<MemberRepository>(relaxed = true)
        val tracker = MemberOnlineTracker(redisson, repo)

        val delayedPingAt = Instant.now().minus(Duration.ofMinutes(25)).truncatedTo(ChronoUnit.MILLIS)

        val zset = mockk<RScoredSortedSet<Long>>(relaxed = true)
        every { redisson.getScoredSortedSet<Long>(any<String>()) } returns zset
        every { zset.entryRange(0.0, true, any(), true) } returns
            listOf(ScoredEntry(delayedPingAt.toEpochMilli().toDouble(), 2L))

        val dirty = mockk<RSet<Long>>(relaxed = true)
        every { redisson.getSet<Long>(any<String>()) } returns dirty
        every { dirty.readAll() } returns emptySet()

        tracker.syncAccessInfo()

        Then("0.0 부터 조회되어 오래된 핑도 누락 없이 DB에 반영된다") {
            verify(exactly = 1) {
                repo.updateAccessInfo(
                    id = 2L,
                    accessedAt = delayedPingAt,
                    ip = null,
                    deviceId = null,
                )
            }
        }

        Then("0.0 부터 지워져 ZSET 메모리 누수가 발생하지 않는다") {
            verify(exactly = 1) { zset.removeRangeByScore(0.0, true, any(), true) }
        }
    }
})

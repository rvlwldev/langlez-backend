package com.langlez.member.application

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
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
import java.time.Instant

/**
 * 접속 시각(핑)과 접속 IP/기기는 같은 `member.audit` 를 건드린다.
 * 따로 돌리면 같은 행을 두 트랜잭션이 각자 merge 해 서로를 덮어쓴다. 한 번에 모아 쓴다.
 */
class MemberAccessSyncTest : BehaviorSpec({

    val redisson = mockk<RedissonClient>(relaxed = true)
    val repo = mockk<MemberRepository>(relaxed = true)
    val tracker = MemberOnlineTracker(redisson, repo)

    val member = Member(
        id = 1L,
        email = "u@test.com",
        handle = "u1",
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    Given("한 회원이 10분 안에 핑도 보내고 로그인도 했으면") {
        val pingAt = Instant.now()

        val zset = mockk<RScoredSortedSet<Long>>(relaxed = true)
        every { redisson.getScoredSortedSet<Long>(any<String>()) } returns zset
        every { zset.entryRange(any(), any(), any(), any()) } returns
            listOf(ScoredEntry(pingAt.toEpochMilli().toDouble(), 1L))

        val dirty = mockk<RSet<Long>>(relaxed = true)
        every { redisson.getSet<Long>(any<String>()) } returns dirty
        every { dirty.readAll() } returns setOf(1L)

        val accessMap = mockk<RMap<String, String>>(relaxed = true)
        every { redisson.getMap<String, String>(any<String>()) } returns accessMap
        every { accessMap.readAllMap() } returns mapOf("ip" to "1.2.3.4", "device" to "device-A")

        every { repo.findAll(any<Collection<Long>>()) } returns listOf(member)

        tracker.syncAccessInfo()

        Then("접속 시각과 IP/기기가 모두 반영된다") {
            member.audit.lastAccessedIp shouldBe "1.2.3.4"
            member.audit.lastDeviceId shouldBe "device-A"
            member.audit.lastAccessedAt shouldBe pingAt.let { Instant.ofEpochMilli(it.toEpochMilli()) }
        }

        Then("저장은 회원당 한 번만 일어난다") {
            verify(exactly = 1) { repo.save(member) }
        }

        Then("조회도 한 번만 한다") {
            verify(exactly = 1) { repo.findAll(any<Collection<Long>>()) }
        }
    }
})

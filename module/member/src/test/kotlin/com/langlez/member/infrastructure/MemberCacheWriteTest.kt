package com.langlez.member.infrastructure

import com.langlez.core.cache.Cache
import com.langlez.core.cache.CacheProvider
import com.langlez.member.domain.Member
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/** 캐시에 무엇이 남았는지가 아니라 **어떤 방식으로 썼는지**를 기록하는 대역. */
private class RecordingCache : Cache {
    val map = mutableMapOf<Any, Any>()
    var puts = 0
    var putsIfAbsent = 0

    override fun <T : Any> get(key: Any, type: Class<T>): T? = map[key]?.let(type::cast)
    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> =
        keys.mapNotNull { k -> map[k]?.let { k to type.cast(it) } }.toMap()

    override fun put(key: Any, value: Any) { puts++; map[key] = value }
    override fun <T : Any> putMany(entries: Map<out Any, T>) { puts += entries.size; map.putAll(entries) }
    override fun putIfAbsent(key: Any, value: Any) { putsIfAbsent++; map.putIfAbsent(key, value) }
    override fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) =
        entries.forEach { (key, value) -> putIfAbsent(key, value) }

    override fun evict(key: Any) { map.remove(key) }
    override fun evictMany(keys: Collection<Any>) { keys.forEach(::evict) }
}

/**
 * 캐시 미스로 DB 에서 읽어온 값(read-through)은 캐시를 덮어쓰지 않아야 하고,
 * 캐시 히트는 캐시에 아무것도 쓰지 않아야 한다.
 *
 * 둘 다 어겨서 정지된 회원이 캐시의 ACTIVE 로 계속 통과했다. 덮어쓰기가 낡은 값을
 * 최종 상태보다 늦게 도착시켰고, 히트 시 되쓰기가 그 낡은 값의 TTL 을 무한히 갱신했다.
 * 실제 순서 경합은 `MemberStatusCacheRaceTest` 가 스레드로 재현한다.
 */
class MemberCacheWriteTest : BehaviorSpec({

    val byName = mutableMapOf<String, RecordingCache>()
    val caches = mockk<CacheProvider>()
    every { caches.getCache(any()) } answers { byName.getOrPut(firstArg()) { RecordingCache() } }

    val jpa = mockk<MemberJpaRepository>()
    val repo = MemberRepositoryImpl(jpa, mockk<JPAQueryFactory>(), caches)
    val members = byName.getValue("member")

    val member = Member(
        id = 1L,
        email = "u1@test.com",
        handle = "alice",
        status = Member.Status.ACTIVE,
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    Given("캐시가 비어 있어 DB 에서 읽어와야 하면") {
        every { jpa.findWithAuditById(1L) } returns member

        val found = repo.find(1L)

        Then("회원을 반환한다") {
            found?.id shouldBe 1L
        }

        // put 으로 적재하면 커밋 전 DB 를 읽은 요청이 커밋 후 갱신을 덮어쓴다.
        Then("덮어쓰지 않는 방식으로만 적재한다") {
            members.puts shouldBe 0
            members.putsIfAbsent shouldBe 1
        }
    }

    Given("이미 캐시에 값이 있으면") {
        val putsBefore = members.puts
        val putsIfAbsentBefore = members.putsIfAbsent

        val found = repo.find(1L)

        Then("캐시 값을 그대로 돌려준다") {
            found?.id shouldBe 1L
        }

        // 되쓰면 TTL 이 갱신돼 낡은 값이 영영 만료되지 않는다. 요청당 Redis SET 4회도 여기서 붙었다.
        Then("캐시에 다시 쓰지 않는다") {
            members.puts shouldBe putsBefore
            members.putsIfAbsent shouldBe putsIfAbsentBefore
        }
    }
})

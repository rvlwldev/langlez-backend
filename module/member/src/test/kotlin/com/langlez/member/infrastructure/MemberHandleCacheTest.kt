package com.langlez.member.infrastructure

import com.langlez.core.cache.Cache
import com.langlez.core.cache.CacheProvider
import com.langlez.member.domain.Member
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import com.querydsl.core.types.EntityPath
import com.querydsl.core.types.Predicate
import com.querydsl.jpa.impl.JPAQuery
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/** 실제 put/get/evict 동작을 그대로 재현하는 인메모리 캐시. */
private class FakeCache : Cache {
    val map = mutableMapOf<Any, Any>()
    override fun <T : Any> get(key: Any, type: Class<T>): T? = map[key]?.let(type::cast)
    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>): Map<Any, T> =
        keys.mapNotNull { k -> map[k]?.let { k to type.cast(it) } }.toMap()

    override fun put(key: Any, value: Any) { map[key] = value }
    override fun <T : Any> putMany(entries: Map<out Any, T>) { map.putAll(entries) }
    override fun putIfAbsent(key: Any, value: Any) { map.putIfAbsent(key, value) }
    override fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) =
        entries.forEach { (key, value) -> putIfAbsent(key, value) }
    override fun evict(key: Any) { map.remove(key) }
    override fun evictMany(keys: Collection<Any>) { keys.forEach(::evict) }
}

private fun queryDsl(result: Member?): Pair<JPAQueryFactory, JPAQuery<Member>> {
    val dsl = mockk<JPAQueryFactory>()
    val query = mockk<JPAQuery<Member>>()
    every { dsl.selectFrom(any<EntityPath<Member>>()) } returns query
    every { query.leftJoin(any<EntityPath<Any>>()) } returns query
    every { query.fetchJoin() } returns query
    every { query.where(any<Predicate>()) } returns query
    every { query.where(any<Predicate>(), any<Predicate>()) } returns query
    every { query.fetchOne() } returns result
    return dsl to query
}

/**
 * handle·email·provider 는 유니크 제약이 걸린 컬럼이라 별도 캐시 인덱스 없이도
 * QueryDSL 조회 한 번이면 찾는다. 캐시가 없으므로 구 handle 이 TTL 까지 남아
 * 낡은 회원을 돌려주는 경로 자체가 존재하지 않는다.
 */
class MemberHandleCacheTest : BehaviorSpec({

    val caches = mockk<CacheProvider>()
    every { caches.getCache(any()) } returns FakeCache()
    val jpa = mockk<MemberJpaRepository>()

    val member = Member(
        id = 1L,
        email = "u1@test.com",
        handle = "bob",
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    Given("handle 로 조회하면") {
        val (dsl, query) = queryDsl(member)
        val repo = MemberRepositoryImpl(jpa, dsl, caches)

        Then("DB 조회 한 번으로 찾는다") {
            repo.find("bob")?.id shouldBe 1L
            verify(exactly = 1) { query.fetchOne() }
        }
    }

    Given("이제 쓰지 않는 구 handle 로 조회하면") {
        val (dsl, _) = queryDsl(null)
        val repo = MemberRepositoryImpl(jpa, dsl, caches)

        Then("찾지 못한다") {
            repo.find("alice") shouldBe null
        }
    }

    Given("email 로 조회하면") {
        val (dsl, query) = queryDsl(member)
        val repo = MemberRepositoryImpl(jpa, dsl, caches)

        Then("DB 조회 한 번으로 찾는다") {
            repo.findByEmail("u1@test.com")?.id shouldBe 1L
            verify(exactly = 1) { query.fetchOne() }
        }
    }

    Given("provider 로 조회하면") {
        val (dsl, query) = queryDsl(member)
        val repo = MemberRepositoryImpl(jpa, dsl, caches)

        Then("DB 조회 한 번으로 찾는다") {
            repo.find(Member.Provider.GOOGLE, "p1")?.id shouldBe 1L
            verify(exactly = 1) { query.fetchOne() }
        }
    }
})

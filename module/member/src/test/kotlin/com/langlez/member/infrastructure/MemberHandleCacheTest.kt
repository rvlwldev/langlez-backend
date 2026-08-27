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

/**
 * handle 은 바뀔 수 있는 키다. 캐시에 남은 구 handle 로 조회하면
 * 이미 그 handle 을 쓰지 않는 회원이 돌아온다(핸들 스쿼팅 창).
 */
class MemberHandleCacheTest : BehaviorSpec({

    val byName = mutableMapOf<String, FakeCache>()
    val caches = mockk<CacheProvider>()
    every { caches.getCache(any()) } answers { byName.getOrPut(firstArg()) { FakeCache() } }

    val jpa = mockk<MemberJpaRepository>()
    val dsl = mockk<JPAQueryFactory>()
    val repo = MemberRepositoryImpl(jpa, dsl, caches)

    val member = Member(
        id = 1L,
        email = "u1@test.com",
        handle = "alice",
        provider = Member.Provider.GOOGLE,
        providerId = "p1",
    )

    Given("alice 로 캐시가 채워진 뒤 핸들을 bob 으로 바꾸면") {
        every { jpa.save(any<Member>()) } answers { firstArg() }
        every { jpa.findWithAuditById(1L) } answers { member }

        // DB 에는 alice 라는 핸들을 쓰는 회원이 더는 없다
        val query = mockk<JPAQuery<Member>>()
        every { dsl.selectFrom(any<EntityPath<Member>>()) } returns query
        every { query.leftJoin(any<EntityPath<Any>>()) } returns query
        every { query.fetchJoin() } returns query
        every { query.where(any<Predicate>()) } returns query
        every { query.fetchOne() } returns null

        repo.save(member) // handles["alice"] = 1
        member.changeHandle("bob")
        repo.save(member) // handles["bob"] = 1

        Then("새 핸들로는 조회된다") {
            repo.find("bob")?.id shouldBe 1L
        }

        Then("구 핸들로는 조회되지 않는다") {
            repo.find("alice") shouldBe null
        }

        Then("낡은 구 핸들 키는 캐시에서 제거된다") {
            byName.getValue("member-handle").map.containsKey("alice") shouldBe false
        }
    }
})

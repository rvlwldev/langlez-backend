package com.langlez.member.infrastructure

import com.github.benmanes.caffeine.cache.Caffeine
import com.langlez.config.JacksonConfiguration
import com.langlez.member.domain.Member
import com.langlez.redis.cache.CaffeineCache
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

/**
 * `member` 캐시가 Redis 장애로 로컬(Caffeine) 폴백을 탈 때를 겨냥한다.
 *
 * `Member.audit` 은 LAZY 지만 [MemberJpaRepository.findWithAuditById] 가 항상 함께 조회해
 * 캐시에 넣기 전에 이미 초기화돼 있다 — 트랜잭션 밖에서 읽어도 초기화 안 된 프록시를 만나지 않는다.
 * 남는 위험은 폴백 캐시가 그 인스턴스를 그대로 돌려줘 호출자의 수정이 다른 호출자에게 새는 것뿐이라,
 * [CaffeineCache] 가 이제 직렬화 왕복으로 막는다.
 */
class MemberEntityCacheIsolationTest : BehaviorSpec({

    // 운영과 다른 직렬화 프로파일을 검증하지 않도록 실제 빈이 쓰는 것과 같은 설정을 재사용한다.
    val mapper = JacksonConfiguration().objectMapper()

    Given("audit 이 채워진 Member 를 로컬 폴백 캐시에 넣으면") {
        val cache = CaffeineCache(Caffeine.newBuilder().build(), mapper)

        val member = Member(
            id = 1L,
            email = "u1@test.com",
            handle = "alice",
            provider = Member.Provider.GOOGLE,
            providerId = "p1",
        )
        member.updateAccessedAt(Instant.parse("2026-01-01T00:00:00Z"))
        cache.put(member.id, member)

        When("트랜잭션 밖에서 다시 꺼내면") {
            val found = cache.get(member.id, Member::class.java)

            Then("LAZY 인 audit 을 예외 없이 읽는다") {
                found.shouldNotBeNull()
                found.audit.lastAccessedAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
            }
        }

        When("꺼낸 값과 그 audit 을 수정하면") {
            val found = cache.get(member.id, Member::class.java)!!
            found.handle = "mutated"
            found.audit.lastAccessedAt = Instant.parse("2099-01-01T00:00:00Z")

            Then("캐시에서 다시 꺼내면 원래 값이다") {
                val again = cache.get(member.id, Member::class.java)!!
                again.handle shouldBe "alice"
                again.audit.lastAccessedAt shouldBe Instant.parse("2026-01-01T00:00:00Z")
            }
        }
    }

    // MemberRepositoryImpl.cacheIfAbsent(read-through 캐시 적재)가 실제로 쓰는 연산이다.
    // put/get 만 검증하면 find(id)/find(handle)/find(provider,id)/findByEmail/findAll(ids)
    // 이 채우는 실제 경로가 무검증으로 남는다.
    Given("audit 이 채워진 Member 를 putIfAbsent(read-through 적재)로 넣으면") {
        val cache = CaffeineCache(Caffeine.newBuilder().build(), mapper)

        val member = Member(
            id = 2L,
            email = "u2@test.com",
            handle = "bob",
            provider = Member.Provider.GOOGLE,
            providerId = "p2",
        )
        member.updateAccessedAt(Instant.parse("2026-01-01T00:00:00Z"))
        cache.putIfAbsent(member.id, member)

        When("꺼낸 값을 수정하면") {
            val found = cache.get(member.id, Member::class.java)!!
            found.handle = "mutated"

            Then("캐시에서 다시 꺼내면 원래 값이다") {
                cache.get(member.id, Member::class.java)!!.handle shouldBe "bob"
            }
        }

        When("같은 id 로 다른 값을 putIfAbsent 하면") {
            val stale = Member(
                id = 2L,
                email = "u2@test.com",
                handle = "stale-overwrite",
                provider = Member.Provider.GOOGLE,
                providerId = "p2",
            )
            cache.putIfAbsent(member.id, stale)

            Then("먼저 채워둔 값이 보존된다") {
                cache.get(member.id, Member::class.java)!!.handle shouldBe "bob"
            }
        }
    }
})

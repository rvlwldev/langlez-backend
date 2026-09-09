package com.langlez.redis.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.langlez.config.JacksonConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/** 캐시에 넣을 엔티티류처럼 참조를 공유하면 위험한, 필드가 변경 가능한 픽스처. */
private class MutableRecord(var name: String, var nested: MutableChild)
private class MutableChild(var note: String)

/**
 * Redis 장애 시 폴백되는 로컬 캐시라 직렬화 없이 힙의 같은 인스턴스를 그대로 돌려주면,
 * 한 호출자가 꺼낸 값을 수정하는 순간 다른 호출자가 보는 캐시 값도 같이 바뀐다.
 * `Cache.get`/`Cache.put` 을 직렬화 왕복으로 강제해 이 공유를 끊는다.
 */
class CaffeineCacheTest : BehaviorSpec({

    // 운영과 다른 직렬화 프로파일(WRITE_DATES_AS_TIMESTAMPS 기본값 등)을 검증하지 않도록
    // 실제 빈이 쓰는 것과 같은 설정을 그대로 재사용한다.
    val mapper = JacksonConfiguration().objectMapper()

    fun newCache() = CaffeineCache(Caffeine.newBuilder().build(), mapper)

    Given("캐시에 값을 넣고 두 번 꺼내면") {
        val cache = newCache()
        val original = MutableRecord(name = "alice", nested = MutableChild(note = "first"))
        cache.put("k1", original)

        When("첫 번째로 꺼낸 객체와 중첩 객체를 수정하면") {
            val first = cache.get("k1", MutableRecord::class.java)!!
            first.name = "mutated"
            first.nested.note = "mutated"

            Then("두 번째로 꺼낸 값은 영향을 받지 않는다") {
                val second = cache.get("k1", MutableRecord::class.java)!!
                second.name shouldBe "alice"
                second.nested.note shouldBe "first"
            }

            Then("원본 인스턴스도 캐시가 반환한 것과 다른 인스턴스였다") {
                (first === original) shouldBe false
            }
        }
    }

    Given("getMany 로 여러 건을 꺼내면") {
        val cache = newCache()
        cache.putMany(mapOf("a" to MutableRecord("a-name", MutableChild("a-note"))))

        When("꺼낸 값을 수정하면") {
            val found = cache.getMany(listOf("a"), MutableRecord::class.java).getValue("a")
            found.name = "changed"

            Then("다시 꺼내면 원래 값이다") {
                cache.getMany(listOf("a"), MutableRecord::class.java).getValue("a").name shouldBe "a-name"
            }
        }
    }

    // MemberRepositoryImpl 의 read-through 적재(find(id)/find(handle)/find(provider,id)/
    // findByEmail/findAll(ids))는 전부 put/putMany 가 아니라 이 두 연산만 쓴다. put/putMany 만
    // 검증하면 프로덕션에서 실제로 캐시를 채우는 경로가 통째로 무검증으로 남는다.
    Given("putIfAbsent 로 값을 넣고 두 번 꺼내면") {
        val cache = newCache()
        val original = MutableRecord(name = "alice", nested = MutableChild(note = "first"))
        cache.putIfAbsent("k2", original)

        When("첫 번째로 꺼낸 객체를 수정하면") {
            val first = cache.get("k2", MutableRecord::class.java)!!
            first.name = "mutated"

            Then("두 번째로 꺼낸 값은 영향을 받지 않는다") {
                cache.get("k2", MutableRecord::class.java)!!.name shouldBe "alice"
            }

            Then("원본 인스턴스도 캐시가 반환한 것과 다른 인스턴스였다") {
                (first === original) shouldBe false
            }
        }

        When("이미 있는 키에 putIfAbsent 를 다시 호출하면") {
            cache.putIfAbsent("k2", MutableRecord(name = "bob", nested = MutableChild(note = "second")))

            Then("기존 값이 보존된다 (읽기 경로가 커밋된 최신 값을 덮어쓰면 안 된다)") {
                cache.get("k2", MutableRecord::class.java)!!.name shouldBe "alice"
            }
        }
    }

    Given("putManyIfAbsent 로 여러 건을 넣으면") {
        val cache = newCache()
        cache.putManyIfAbsent(mapOf("m1" to MutableRecord("m1-name", MutableChild("m1-note"))))

        When("꺼낸 값을 수정하면") {
            val found = cache.getMany(listOf("m1"), MutableRecord::class.java).getValue("m1")
            found.name = "changed"

            Then("다시 꺼내면 원래 값이다") {
                cache.getMany(listOf("m1"), MutableRecord::class.java).getValue("m1").name shouldBe "m1-name"
            }
        }

        When("이미 있는 키를 포함해 putManyIfAbsent 를 다시 호출하면") {
            cache.putManyIfAbsent(mapOf("m1" to MutableRecord("overwritten", MutableChild("x"))))

            Then("기존 값이 보존된다") {
                cache.getMany(listOf("m1"), MutableRecord::class.java).getValue("m1").name shouldBe "m1-name"
            }
        }
    }
})

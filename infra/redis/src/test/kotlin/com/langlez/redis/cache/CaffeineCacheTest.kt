package com.langlez.redis.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.benmanes.caffeine.cache.Caffeine
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

    val mapper = ObjectMapper().findAndRegisterModules()

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
})

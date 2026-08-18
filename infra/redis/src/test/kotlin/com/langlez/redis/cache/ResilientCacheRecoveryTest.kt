package com.langlez.redis.cache

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.redisson.api.RBucket
import org.redisson.api.RKeys
import org.redisson.api.RedissonClient

/**
 * 레디스 복구 시 로컬 캐시를 레디스로 올리면 안 된다.
 *
 * 로컬 Caffeine 은 프로세스마다 따로다. 장애 중 다른 노드가 지운 키가 이 노드 로컬엔 남아 있을 수 있고,
 * 롤백된 트랜잭션이 쓴 값도 남아 있을 수 있다. 그걸 복구 때 올리면 프로세스 안에만 있던
 * 낡은 값이 전역으로 승격된다. 캐시는 버려도 되는 데이터이므로 복구 시 그냥 폐기한다.
 */
class ResilientCacheRecoveryTest : BehaviorSpec({

    fun newRedisson(): Pair<RedissonClient, RKeys> {
        val keys = mockk<RKeys>(relaxed = true)
        val bucket = mockk<RBucket<Any>>(relaxed = true)
        val redisson = mockk<RedissonClient>(relaxed = true)

        every { redisson.keys } returns keys
        every { redisson.getBucket<Any>(any<String>()) } returns bucket
        every { bucket.get() } returns null
        return redisson to keys
    }

    Given("레디스 장애 중 로컬에만 값이 쌓였다가 복구되면") {
        val (redisson, keys) = newRedisson()
        val provider = ResilientCacheProvider(redisson, SimpleMeterRegistry())
        val cache = provider.getCache("member")

        // 1) 장애 발생 — 헬스체크 실패로 다운 표시
        every { keys.countExists(any()) } throws RuntimeException("redis down")
        provider.checkRedisHealth()

        // 2) 장애 중 쓰기 — 로컬로 폴백된다
        cache.put("k1", "stale-value")

        // 3) 복구
        every { keys.countExists(any()) } returns 1L
        provider.checkRedisHealth()

        Then("로컬 값을 레디스로 올리지 않는다") {
            // 이관은 RedisCache.putMany → redisson.createBatch() 로 나간다
            verify(exactly = 0) { redisson.createBatch() }
        }

        Then("복구 후 다시 장애가 나도 그 값은 남아 있지 않다") {
            every { keys.countExists(any()) } throws RuntimeException("redis down again")
            provider.checkRedisHealth()

            cache.get("k1", String::class.java) shouldBe null
        }
    }
})

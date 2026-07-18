package com.langlez.redis.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

class MemberPresenceTrackerImplTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val tracker = MemberPresenceTrackerImpl(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("MemberPresenceTrackerImpl 테스트") {

        When("markOnline을 호출하면") {
            val memberId = 42L

            Then("isOnline이 true를 반환한다") {
                tracker.isOnline(memberId) shouldBe false
                tracker.markOnline(memberId)
                tracker.isOnline(memberId) shouldBe true
            }
        }

        When("markOnline을 호출하지 않은 memberId인 경우") {
            val memberId = 999L

            Then("isOnline이 false를 반환한다") {
                tracker.isOnline(memberId) shouldBe false
            }
        }

        When("온라인인 회원과 오프라인인 회원들이 있을 때 countOnline을 호출하면") {
            val beforeCount = tracker.countOnline()
            tracker.markOnline(101L)
            tracker.markOnline(102L)

            Then("온라인 회원 수가 정상 집계되어야 한다") {
                tracker.countOnline() shouldBe beforeCount + 2
            }
        }
    }
})

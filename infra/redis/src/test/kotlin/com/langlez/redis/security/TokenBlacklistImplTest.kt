package com.langlez.redis.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

class TokenBlacklistImplTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val tokenBlacklist = TokenBlacklistImpl(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("TokenBlacklistImpl 테스트") {

        When("토큰을 blacklist할 때") {
            val token = "some-jwt-token"

            Then("isBlacklisted가 true를 반환한다") {
                tokenBlacklist.isBlacklisted(token) shouldBe false
                tokenBlacklist.blacklist(token, 10L)
                tokenBlacklist.isBlacklisted(token) shouldBe true
            }
        }

        When("blacklist 하지 않은 토큰인 경우") {
            val token = "unblacklisted-token"

            Then("isBlacklisted가 false를 반환한다") {
                tokenBlacklist.isBlacklisted(token) shouldBe false
            }
        }

        When("remainingValiditySeconds가 0 이하인 경우") {
            val token = "zero-validity-token"

            Then("blacklist에 추가되지 않고 isBlacklisted가 false를 반환한다") {
                tokenBlacklist.blacklist(token, 0L)
                tokenBlacklist.isBlacklisted(token) shouldBe false

                tokenBlacklist.blacklist(token, -5L)
                tokenBlacklist.isBlacklisted(token) shouldBe false
            }
        }
    }
})

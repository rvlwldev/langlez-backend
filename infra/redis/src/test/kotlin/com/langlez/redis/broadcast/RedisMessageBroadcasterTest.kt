package com.langlez.redis.broadcast

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.redis.config.RedissonConfiguration
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.testcontainers.containers.GenericContainer
import kotlin.time.Duration.Companion.seconds

/**
 * 인스턴스 2대를 실제 레디스 하나에 붙여 팬아웃을 검증한다.
 * 로컬 템플릿만 확인하는 테스트는 STOMP 인메모리 브로커 버그(다른 인스턴스에 안 닿음)를
 * 그대로 통과시키므로, 반드시 발행자와 다른 인스턴스의 템플릿을 검증해야 한다.
 */
class RedisMessageBroadcasterTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    // 프로덕션 코덱을 그대로 쓴다. 다른 코덱으로 바꾸면 봉투 직렬화 결함을 못 잡는다.
    fun client(): RedissonClient = Redisson.create(
        Config().apply {
            codec = RedissonConfiguration.redisCodec(
                ObjectMapper().registerKotlinModule().findAndRegisterModules()
            )
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val redissonA = client()
    val redissonB = client()

    afterSpec {
        redissonA.shutdown()
        redissonB.shutdown()
        redis.stop()
    }

    Given("인스턴스 2개가 같은 레디스를 보고 있을 때") {
        val stompA = mockk<SimpMessagingTemplate>(relaxed = true)
        val stompB = mockk<SimpMessagingTemplate>(relaxed = true)

        val a = RedisMessageBroadcaster(redissonA, stompA).also { it.subscribe() }
        RedisMessageBroadcaster(redissonB, stompB).also { it.subscribe() }

        When("A 에서 발행하면") {
            a.broadcast("/topic/chat/1", mapOf("text" to "hi"))

            Then("B 에 붙은 세션에도 전달된다") {
                eventually(3.seconds) {
                    verify { stompB.convertAndSend("/topic/chat/1", mapOf("text" to "hi") as Any) }
                }
            }

            Then("발행한 A 자신도 구독을 통해 되돌려받는다 (로컬 직접 전달 금지)") {
                eventually(3.seconds) {
                    verify(exactly = 1) { stompA.convertAndSend("/topic/chat/1", any<Any>()) }
                }
            }
        }
    }
})

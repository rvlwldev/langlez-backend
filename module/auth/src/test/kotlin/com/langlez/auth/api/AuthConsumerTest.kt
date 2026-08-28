package com.langlez.auth.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.langlez.auth.application.AuthService
import com.langlez.core.MessageDeduplicator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.Runs
import io.mockk.andThenJust
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class AuthConsumerTest : BehaviorSpec({

    val service = mockk<AuthService>(relaxed = true)

    // SETNX 를 흉내 내는 로컬 대역. relaxed 목이면 isDuplicate 가 늘 false 라
    // "두 번 들어와도 한 번만" 이 검증되지 않는다.
    val seen = mutableSetOf<String>()
    val dedup = object : MessageDeduplicator {
        override fun isDuplicate(topic: String, payload: String) = !seen.add(topic + payload)
        override fun release(topic: String, payload: String) {
            seen.remove(topic + payload)
        }
    }

    val consumer = AuthConsumer(service, dedup, jacksonObjectMapper())

    afterEach {
        clearMocks(service, answers = false)
        seen.clear()
    }

    fun payload(id: Long) = """{"id":$id}"""

    Given("member-withdrawn 토픽 메시지가 들어오면") {

        When("페이로드가 MemberWithdrawnEvent JSON 이면") {
            Then("회원의 세션(리프레시 토큰·기기 바인딩)을 지운다") {
                consumer.onMemberWithdrawn(payload(1L))

                verify(exactly = 1) { service.invalidateSession(1L) }
            }
        }

        When("같은 메시지가 두 번 배달되면") {
            Then("세션 무효화는 한 번만 일어난다") {
                consumer.onMemberWithdrawn(payload(2L))
                consumer.onMemberWithdrawn(payload(2L))

                verify(exactly = 1) { service.invalidateSession(2L) }
            }
        }

        When("처리 도중 예외가 나면") {
            Then("예외를 그대로 올리고, 재배달된 메시지는 중복으로 걸리지 않는다") {
                every { service.invalidateSession(any()) } throws
                    IllegalStateException("Redis 장애") andThenJust Runs

                shouldThrow<IllegalStateException> { consumer.onMemberWithdrawn(payload(3L)) }

                consumer.onMemberWithdrawn(payload(3L))

                verify(exactly = 2) { service.invalidateSession(3L) }
            }
        }
    }
})

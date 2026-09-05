package com.langlez.chat

import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.redisson.api.listener.MessageListener
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession

/**
 * 세션 추적과 강제 종료.
 *
 * 끊는 쪽과 소켓이 붙은 쪽이 다른 인스턴스일 수 있어, 종료는 레디스 채널을 한 번 돈 뒤에
 * 각자 자기 세션에만 적용돼야 한다. 여기서는 그 수신부(리스너)를 직접 태워 확인한다.
 */
class WebSocketSessionRegistryTest : BehaviorSpec({

    val topic = mockk<RTopic>(relaxed = true)
    val redisson = mockk<RedissonClient>().also { every { it.getTopic(any<String>()) } returns topic }

    val listener = slot<MessageListener<WebSocketSessionRegistry.Signal>>()
    every {
        topic.addListener(WebSocketSessionRegistry.Signal::class.java, capture(listener))
    } returns 1

    val registry = WebSocketSessionRegistry(redisson).also { it.subscribe() }

    fun session(id: String) = mockk<WebSocketSession>(relaxed = true).also { every { it.id } returns id }

    /** 레디스를 거쳐 돌아온 신호를 흉내 낸다. 발행자 자신도 구독을 통해 되받는다. */
    fun deliver(memberId: Long) =
        listener.captured.onMessage("websocket-session-terminate", WebSocketSessionRegistry.Signal(memberId))

    Given("회원이 세션 두 개로 붙어 있고 다른 회원도 하나 붙어 있을 때") {

        val first = session("s1")
        val second = session("s2")
        val stranger = session("s3")

        registry.register(first)
        registry.register(second)
        registry.register(stranger)
        registry.bind("s1", 1L)
        registry.bind("s2", 1L)
        registry.bind("s3", 2L)

        When("그 회원의 종료 신호가 도착하면") {
            deliver(1L)

            Then("그 회원의 세션이 전부 끊긴다") {
                verify { first.close(CloseStatus.POLICY_VIOLATION) }
                verify { second.close(CloseStatus.POLICY_VIOLATION) }
            }

            // 회원 단위로 걸러내지 않으면 정지 한 건이 무관한 사용자까지 튕긴다.
            Then("다른 회원의 세션은 남는다") {
                verify(exactly = 0) { stranger.close(any()) }
            }

            Then("같은 신호가 다시 와도 이미 끊긴 세션을 다시 닫지 않는다") {
                deliver(1L)
                verify(exactly = 1) { first.close(CloseStatus.POLICY_VIOLATION) }
            }
        }
    }

    Given("인증을 마치지 않아 주인이 없는 세션은") {

        val anonymous = session("s9")
        registry.register(anonymous)

        When("아무 회원의 종료 신호가 와도") {
            deliver(1L)

            Then("끊기지 않는다") {
                verify(exactly = 0) { anonymous.close(any()) }
            }
        }
    }

    Given("소켓이 먼저 닫혀 등록이 풀린 뒤에는") {

        val closed = session("s8")
        registry.register(closed)
        registry.bind("s8", 8L)
        registry.unregister("s8")

        When("그 회원의 종료 신호가 와도") {
            deliver(8L)

            Then("이미 사라진 세션을 건드리지 않는다") {
                verify(exactly = 0) { closed.close(any()) }
            }
        }
    }

    Given("정지 조치가 내려지면") {

        When("terminate 를 부르면") {
            registry.terminate(5L)

            // 끊는 쪽과 소켓이 붙은 쪽이 다른 인스턴스일 수 있다. 로컬에서 바로 끊으면
            // 다른 인스턴스에 붙은 세션이 그대로 살아남는다.
            Then("로컬에서 바로 끊지 않고 레디스로 전파한다") {
                verify { topic.publish(WebSocketSessionRegistry.Signal(5L)) }
            }
        }
    }
})

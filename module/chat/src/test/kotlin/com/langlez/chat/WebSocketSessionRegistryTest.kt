package com.langlez.chat

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.redisson.api.RTopic
import org.redisson.api.RedissonClient
import org.redisson.api.listener.MessageListener
import org.slf4j.LoggerFactory
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

        val local = session("s5")
        registry.register(local)
        registry.bind("s5", 5L)

        When("terminate 를 부르면") {
            registry.terminate(5L)

            // 다른 인스턴스에 붙은 세션도 끊어야 한다.
            Then("레디스로 전파한다") {
                verify { topic.publish(WebSocketSessionRegistry.Signal(5L)) }
            }

            // 전파에만 의존하면 레디스가 순단일 때 정지를 처리한 바로 그 서버의 세션조차 안 끊긴다.
            Then("이 인스턴스의 세션은 전파를 기다리지 않고 끊는다") {
                verify { local.close(CloseStatus.POLICY_VIOLATION) }
            }
        }
    }

    /**
     * `terminate` 는 AFTER_COMMIT 리스너에서 불린다. 여기서 던지면 이미 커밋된 정지 API 가 500 을 내고,
     * 운영자가 재시도해도 `member.already-suspended` 로 400 이라 손 쓸 방법이 없다.
     */
    Given("레디스 전파가 실패하면") {

        val appender = ListAppender<ILoggingEvent>().also { it.start() }
        val registryLogger = LoggerFactory.getLogger(WebSocketSessionRegistry::class.java) as Logger
        registryLogger.addAppender(appender)

        val orphan = session("s6")
        registry.register(orphan)
        registry.bind("s6", 6L)

        every { topic.publish(WebSocketSessionRegistry.Signal(6L)) } throws IllegalStateException("redis down")

        When("terminate 를 부르면") {
            registry.terminate(6L)

            Then("예외가 호출자로 나가지 않는다") {
                // 여기까지 왔다는 것 자체가 검증이다. 던졌으면 이 Given 블록이 통째로 실패한다.
                verify { topic.publish(WebSocketSessionRegistry.Signal(6L)) }
            }

            Then("로컬 세션은 그대로 끊긴다") {
                verify { orphan.close(CloseStatus.POLICY_VIOLATION) }
            }

            // 보안 조치가 조용히 실패하면 안 된다. warn 은 묻힌다.
            Then("error 로 남는다") {
                appender.list.filter { it.level == Level.ERROR }
                    .map { it.formattedMessage } shouldContain "실시간 세션 강제 종료 전파 실패. member=6"
            }

            registryLogger.detachAppender(appender)
        }
    }

    /**
     * 소켓은 CONNECT 프레임보다 먼저 열리므로 `register` 는 항상 `bind` 보다 앞선다.
     * 그래서 bind 시점에 세션이 없다는 건 "아직 안 열렸다"가 아니라 "이미 닫혔다"는 뜻이다.
     *
     * CONNECT 인증 중 `findStatus` 를 기다리는 사이 클라이언트가 끊기면 `afterConnectionClosed` →
     * `unregister` 가 먼저 돌고, 뒤늦게 `bind` 가 owners 에 항목을 남긴다. 그 뒤로는 다시
     * `unregister` 가 불리지 않아 영구 잔존한다 — 감사 B-07(wave 참여자 누수)과 같은 종류다.
     */
    Given("CONNECT 인증 도중 소켓이 먼저 닫히면") {

        val aborted = session("s7")
        registry.register(aborted)
        registry.unregister("s7")

        When("뒤늦게 bind 가 불리면") {
            registry.bind("s7", 7L)

            Then("주인 기록이 남지 않는다") {
                registry.trackedOwnerIds() shouldNotContain "s7"
            }
        }
    }

    Given("정상적으로 끝난 세션은") {

        val normal = session("s10")
        registry.register(normal)
        registry.bind("s10", 10L)

        When("소켓이 닫히면") {
            registry.unregister("s10")

            Then("주인 기록도 함께 지워진다") {
                registry.trackedOwnerIds() shouldNotContain "s10"
            }
        }
    }

    Given("강제 종료된 세션은") {

        val killed = session("s11")
        registry.register(killed)
        registry.bind("s11", 11L)

        When("closeLocal 이 끊고 나면") {
            deliver(11L)

            Then("주인 기록이 남지 않는다") {
                registry.trackedOwnerIds() shouldNotContain "s11"
            }
        }
    }
})

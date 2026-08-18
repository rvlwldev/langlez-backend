package com.langlez.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.member.domain.MemberRepository
import com.langlez.redis.config.RedissonConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import java.time.Duration

/**
 * 화면(viewing) 상태는 레디스에 실제로 쓰고 읽어야 의미가 있다.
 * Redisson 을 목으로 대체하면 제네릭이 지워져 코덱이 Long 을 Integer 로 되돌리는 류의
 * 실제 결함을 전혀 못 잡는다. 그래서 프로덕션 코덱 그대로 컨테이너에 붙인다.
 */
class MemberOnlineTrackerViewingTest : BehaviorSpec({

    val redis = GenericContainer("redis:7-alpine").withExposedPorts(6379)
    redis.start()

    val redisson: RedissonClient = Redisson.create(
        Config().apply {
            codec = RedissonConfiguration.redisCodec(
                ObjectMapper().registerKotlinModule().findAndRegisterModules()
            )
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val tracker = MemberOnlineTracker(redisson, mockk<MemberRepository>(relaxed = true))

    afterSpec {
        redisson.shutdown()
        redis.stop()
    }

    Given("회원이 채팅방 토픽을 구독하면") {
        val topic = "/topic/chat/room/1"
        tracker.toOnline(1L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(1L, topic)

        Then("그 방을 보고 있는 사람으로 잡힌다") {
            tracker.viewers(topic) shouldContainExactlyInAnyOrder setOf(1L)
        }

        When("구독을 해제하면") {
            tracker.clearViewing(1L, topic)

            Then("보고 있는 사람에서 빠진다") {
                tracker.viewers(topic).shouldBeEmpty()
            }
        }
    }

    Given("두 회원이 같은 방을 보고 있으면") {
        val topic = "/topic/chat/room/2"
        tracker.toOnline(1L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(1L, topic)
        tracker.toOnline(2L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(2L, topic)

        Then("둘 다 보고 있는 사람으로 잡힌다") {
            tracker.viewers(topic) shouldContainExactlyInAnyOrder setOf(1L, 2L)
        }

        When("한 명만 해제하면") {
            tracker.clearViewing(1L, topic)

            Then("나머지 한 명은 그대로 남는다") {
                tracker.viewers(topic) shouldContainExactlyInAnyOrder setOf(2L)
            }
        }
    }

    Given("한 회원이 여러 방을 보던 중 연결이 끊기면") {
        val roomA = "/topic/chat/room/10"
        val roomB = "/topic/chat/room/11"

        tracker.toOnline(3L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(3L, roomA)
        tracker.toOnline(3L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(3L, roomB)
        tracker.toOnline(4L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(4L, roomB)

        tracker.clearAllViewing(3L)

        Then("그 회원은 자기가 보던 모든 방에서 빠진다") {
            tracker.viewers(roomA).shouldBeEmpty()
            tracker.viewers(roomB) shouldNotContain 3L
        }

        Then("같은 방을 보던 다른 회원은 건드리지 않는다") {
            tracker.viewers(roomB) shouldContainExactlyInAnyOrder setOf(4L)
        }
    }

    Given("아무도 보고 있지 않은 방은") {
        Then("빈 집합을 돌려준다") {
            tracker.viewers("/topic/chat/room/999").shouldBeEmpty()
        }
    }

    Given("아무 방도 보고 있지 않은 회원을 정리해도") {
        Then("아무 일도 일어나지 않는다") {
            tracker.clearAllViewing(9999L)
            tracker.viewers("/topic/chat/room/1").shouldBeEmpty()
        }
    }

    // 구독은 한 번뿐이라, TTL 이 핑으로 갱신되지 않으면 한 방을 TTL 보다 오래 보고 있는 사람이
    // 조용히 목록에서 사라져 "보고 있는 방의 알림"을 받게 된다. 이 갱신이 그 구멍을 막는다.
    Given("구독한 지 오래돼 화면 상태 키의 만료가 코앞이어도") {
        val topic = "/topic/chat/room/20"
        tracker.toOnline(5L)   // 구독 전에 접속 상태여야 한다(핑)
        tracker.recordViewing(5L, topic)

        redisson.getSet<String>("viewing:$topic").expire(Duration.ofSeconds(1))
        redisson.getSet<String>("viewing:member:5").expire(Duration.ofSeconds(1))

        When("핑이 오면") {
            tracker.toOnline(5L)

            Then("만료가 다시 미뤄진다") {
                (redisson.getSet<String>("viewing:$topic").remainTimeToLive() > 60_000) shouldBe true
                (redisson.getSet<String>("viewing:member:5").remainTimeToLive() > 60_000) shouldBe true
            }
        }
    }

    Given("보는 중이던 사용자의 앱이 죽어 핑이 끊기면") {
        val topic = "/topic/chat/room/900"

        Then("보는 중 목록에서 빠진다") {
            // 같은 방을 보는 다른 사람의 핑이 TTL 을 계속 살려두면
            // 죽은 사용자가 영원히 "보는 중"으로 남아 푸시를 못 받는다.
            tracker.toOnline(901L)
            tracker.recordViewing(901L, topic)
            tracker.recordViewing(902L, topic)   // 902 는 핑을 보낸 적이 없다(= 접속 아님)

            tracker.viewers(topic) shouldBe setOf(901L)
        }
    }
})

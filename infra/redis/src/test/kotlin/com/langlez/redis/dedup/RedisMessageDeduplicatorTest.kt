package com.langlez.redis.dedup

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

class RedisMessageDeduplicatorTest : BehaviorSpec({

    val container = GenericContainer("redis:7.0").withExposedPorts(6379)
    container.start()

    val redisson: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${container.host}:${container.getMappedPort(6379)}")
        }
    )

    val dedup = RedisMessageDeduplicator(redisson)

    afterSpec {
        redisson.shutdown()
        container.stop()
    }

    fun payload(followId: Long) = """{"followId":$followId,"followerId":1,"followedId":2}"""

    Given("같은 메시지가 두 번 들어오면") {

        When("연달아 검사하면") {
            Then("첫 번째만 통과하고 두 번째는 중복이다") {
                dedup.isDuplicate("member-followed", payload(1L)) shouldBe false
                dedup.isDuplicate("member-followed", payload(1L)) shouldBe true
            }
        }
    }

    Given("언팔로우 후 재팔로우처럼 followId 만 다른 메시지면") {

        When("검사하면") {
            Then("서로 다른 메시지로 보고 둘 다 통과한다") {
                dedup.isDuplicate("member-followed", payload(2L)) shouldBe false
                dedup.isDuplicate("member-followed", payload(3L)) shouldBe false
            }
        }
    }

    Given("페이로드가 같아도 토픽이 다르면") {

        When("검사하면") {
            Then("서로를 막지 않는다") {
                dedup.isDuplicate("topic-a", payload(4L)) shouldBe false
                dedup.isDuplicate("topic-b", payload(4L)) shouldBe false
            }
        }
    }

    Given("처리가 실패해 표시를 되돌리면") {

        When("같은 메시지가 재배달되면") {
            Then("중복으로 걸리지 않고 다시 처리된다") {
                dedup.isDuplicate("member-followed", payload(5L)) shouldBe false
                dedup.release("member-followed", payload(5L))

                dedup.isDuplicate("member-followed", payload(5L)) shouldBe false
            }
        }
    }

    Given("레디스가 죽어 검사 자체가 실패하면") {

        val broken = mockk<RedissonClient>()
        every { broken.getBucket<String>(any(), any()) } throws IllegalStateException("Redis down")

        When("중복 검사를 하면") {
            Then("막지 않고 통과시킨다 (fail-open)") {
                // 여기서 true 를 돌려주면 장애 시간 동안의 알림이 통째로 사라진다.
                // 오프셋이 커밋되므로 되살릴 방법이 없다.
                RedisMessageDeduplicator(broken).isDuplicate("member-followed", payload(9L)) shouldBe false
            }
        }

        When("표시 해제가 실패하면") {
            Then("예외를 밖으로 올리지 않는다 (원래 실패 원인을 덮으면 안 된다)") {
                RedisMessageDeduplicator(broken).release("member-followed", payload(9L))
            }
        }
    }

    Given("중복 표시를 남기면") {

        When("TTL 을 보면") {
            Then("영구 키가 아니라 1시간 만료가 걸려 있다") {
                dedup.isDuplicate("member-followed", payload(6L))

                // 키 규칙은 구현 세부지만, TTL 이 빠지면 레디스가 무한히 커지는 걸 아무도 못 본다.
                val keys = redisson.keys.getKeysByPattern("dedup:member-followed:*").toList()
                val ttl = keys.map { redisson.getBucket<String>(it, StringCodec.INSTANCE).remainTimeToLive() }.max()

                ttl shouldBeGreaterThan 0
                ttl shouldBeLessThanOrEqual 3_600_000
            }
        }
    }
})

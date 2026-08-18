package com.langlez.wave.infrastructure

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.redis.config.RedissonConfiguration
import com.langlez.wave.domain.WaveChat
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import org.redisson.Redisson
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 * 사라지는 채팅의 저장소.
 *
 * 스프링 컨텍스트 없이 레디스만 띄운다 — 검증 대상이 링버퍼의 잘림·소멸이라 DB 도 웹도 필요 없다.
 */
class WaveSessionRepositoryImplTest : BehaviorSpec({

    val redis = GenericContainer(DockerImageName.parse("redis:7-alpine"))
        .withExposedPorts(6379)
        .also { it.start() }

    val redisson = Redisson.create(
        Config().apply {
            codec = RedissonConfiguration.redisCodec(ObjectMapper().findAndRegisterModules())
            useSingleServer().address = "redis://${redis.host}:${redis.getMappedPort(6379)}"
        }
    )

    val sessions = WaveSessionRepositoryImpl(redisson)

    afterSpec {
        redisson.shutdown()
        redis.stop()
    }

    Given("정원보다 많은 채팅이 오가면") {
        val roomId = 1L
        val overflow = WaveSessionRepositoryImpl.CAPACITY + 5
        repeat(overflow) { sessions.appendChat(roomId, WaveChat(roomId, 1L, "메시지 $it")) }

        Then("최근 N 개만 남고 오래된 것부터 밀려난다") {
            val chats = sessions.recentChats(roomId)

            chats shouldHaveSize WaveSessionRepositoryImpl.CAPACITY
            chats.first().content shouldBe "메시지 5"
            chats.last().content shouldBe "메시지 ${overflow - 1}"
        }

        Then("방이 죽어도 알아서 사라지도록 TTL 이 걸려 있다") {
            redisson.getList<WaveChat>("wave:room:$roomId:chats").remainTimeToLive() shouldBeGreaterThan 0
        }
    }

    Given("방이 끝나면") {
        val roomId = 2L
        sessions.join(roomId, 7L)
        sessions.appendChat(roomId, WaveChat(roomId, 7L, "곧 사라질 말"))

        When("세션을 정리하면") {
            sessions.clear(roomId)

            Then("대화도 참여자도 남지 않는다") {
                sessions.recentChats(roomId) shouldHaveSize 0
                sessions.participants(roomId) shouldHaveSize 0
            }
        }
    }

    Given("참여자를 관리할 때") {
        val roomId = 3L

        Then("같은 사람이 두 번 입장해도 한 명이다") {
            sessions.join(roomId, 5L)
            sessions.join(roomId, 5L)
            sessions.join(roomId, 6L)

            sessions.participants(roomId) shouldContainExactly setOf(5L, 6L)
            sessions.isParticipant(roomId, 5L) shouldBe true
        }

        Then("나가면 참여자에서 빠진다") {
            sessions.leave(roomId, 5L)

            sessions.isParticipant(roomId, 5L) shouldBe false
            sessions.participants(roomId).size shouldBeGreaterThan 0
        }
    }
})

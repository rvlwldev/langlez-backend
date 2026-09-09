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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

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

        Then("새 메시지를 추가하면 가장 오래된 것이 밀리고 새 메시지가 끝에 유지된다") {
            sessions.appendChat(roomId, WaveChat(roomId, 1L, "가장 최신 메시지"))
            val chats = sessions.recentChats(roomId)

            chats shouldHaveSize WaveSessionRepositoryImpl.CAPACITY
            chats.first().content shouldBe "메시지 6"
            chats.last().content shouldBe "가장 최신 메시지"
        }

        Then("방이 죽어도 알아서 사라지도록 TTL 이 걸려 있다") {
            redisson.getList<WaveChat>("wave:room:$roomId:chats").remainTimeToLive() shouldBeGreaterThan 0
        }
    }

    Given("정원 이하의 채팅이 오가면") {
        val roomId = 15L
        val count = 5
        repeat(count) { sessions.appendChat(roomId, WaveChat(roomId, 1L, "메시지 $it")) }

        Then("메시지가 잘리지 않고 전부 유지된다") {
            val chats = sessions.recentChats(roomId)

            chats shouldHaveSize count
            chats.first().content shouldBe "메시지 0"
            chats.last().content shouldBe "메시지 ${count - 1}"
        }
    }

    Given("여러 스레드가 동시에 채팅을 추가하면") {
        val roomId = 16L
        val totalMessages = WaveSessionRepositoryImpl.CAPACITY + 50
        val ready = CountDownLatch(totalMessages)
        val go = CountDownLatch(1)
        val done = CountDownLatch(totalMessages)

        Executors.newVirtualThreadPerTaskExecutor().use { pool ->
            repeat(totalMessages) { i ->
                pool.submit {
                    ready.countDown()
                    go.await()
                    try {
                        sessions.appendChat(roomId, WaveChat(roomId, 1L, "동시 메시지 $i"))
                    } finally {
                        done.countDown()
                    }
                }
            }

            ready.await()
            go.countDown()
            done.await()
        }

        Then("최신 CAPACITY 개만 유지된다") {
            val chats = sessions.recentChats(roomId)

            chats shouldHaveSize WaveSessionRepositoryImpl.CAPACITY
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

    Given("정원 검사와 등록을 한 번에 맡길 때") {

        Then("자리가 남아 있으면 넣는다") {
            val roomId = 10L

            sessions.joinIfNotFull(roomId, 1L, 2) shouldBe true
            sessions.isParticipant(roomId, 1L) shouldBe true
        }

        Then("정원이 찼으면 넣지 않고 거절을 알린다") {
            val roomId = 11L
            sessions.joinIfNotFull(roomId, 1L, 2) shouldBe true
            sessions.joinIfNotFull(roomId, 2L, 2) shouldBe true

            sessions.joinIfNotFull(roomId, 3L, 2) shouldBe false
            sessions.participants(roomId) shouldContainExactly setOf(1L, 2L)
        }

        Then("이미 참여 중이면 정원이 차 있어도 되돌아올 수 있다") {
            val roomId = 12L
            sessions.joinIfNotFull(roomId, 1L, 1) shouldBe true

            // 재입장을 정원으로 막으면 끊긴 사람이 자기가 차지한 자리 때문에 다시 못 들어온다.
            sessions.joinIfNotFull(roomId, 1L, 1) shouldBe true
            sessions.participants(roomId) shouldContainExactly setOf(1L)
        }

        Then("방을 연 사람(join)과 같은 집합에 담긴다") {
            val roomId = 13L
            sessions.join(roomId, 1L)

            // 스크립트와 RSet 이 서로 다른 코덱을 쓰면 같은 회원이 두 번 담겨 정원이 조용히 샌다.
            sessions.joinIfNotFull(roomId, 1L, 2) shouldBe true
            sessions.joinIfNotFull(roomId, 2L, 2) shouldBe true
            sessions.joinIfNotFull(roomId, 3L, 2) shouldBe false

            sessions.participants(roomId) shouldContainExactly setOf(1L, 2L)
        }

        Then("TTL 이 걸려 방이 죽어도 참여자가 남지 않는다") {
            val roomId = 14L
            sessions.joinIfNotFull(roomId, 1L, 2) shouldBe true

            redisson.getSet<String>("wave:room:$roomId:participants").remainTimeToLive() shouldBeGreaterThan 0
        }
    }
})

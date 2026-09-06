package com.langlez.wave.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import com.langlez.redis.config.RedissonConfiguration
import com.langlez.wave.domain.WaveRepository
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.infrastructure.WaveSessionRepositoryImpl
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.redisson.Redisson
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors

/**
 * 동시 입장 경합.
 *
 * 조용한 실패를 두 방향에서 막는다.
 *
 * 1. **정원이 새지 않는가** — 검사와 등록이 갈라져 있으면 두 사람이 동시에 마지막 자리에 들어간다.
 * 2. **성공을 받은 사람이 실제로 참여자인가** — `join` 은 `Unit` 이라 컨트롤러가 204 를 낸다.
 *    본문이 안 돌고도 정상 반환하면 사용자는 들어갔다고 믿지만 이후 구독·채팅이 전부 403 이다.
 *    "예외를 안 받았다 == 참여자다" 를 여기서 못 박는다.
 *
 * 레디스를 실제로 띄운다 — 검증 대상이 원자성이라 목으로는 아무것도 증명하지 못한다.
 */
class WaveJoinConcurrencyTest : BehaviorSpec({

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
    val repo = mockk<WaveRepository>()
    val service = WaveService(repo, sessions, mockk<MessageBroadcaster>(relaxed = true))

    afterSpec {
        redisson.shutdown()
        redis.stop()
    }

    val roomId = 1L
    val capacity = WaveRoom.MIN_PARTICIPANTS

    Given("정원 $capacity 인 방에 ${capacity * 2} 명이 같은 순간에 입장하면") {
        every { repo.find(roomId) } returns
            WaveRoom(id = roomId, broadcasterId = 1L, title = "영어 수다방", maxParticipants = capacity)

        val contenders = (1L..capacity * 2L).toList()
        val succeeded = ConcurrentLinkedQueue<Long>()
        val rejected = ConcurrentLinkedQueue<Int>()

        val ready = CountDownLatch(contenders.size)
        val go = CountDownLatch(1)
        val done = CountDownLatch(contenders.size)

        Executors.newVirtualThreadPerTaskExecutor().use { pool ->
            contenders.forEach { memberId ->
                pool.submit {
                    ready.countDown()
                    go.await()

                    try {
                        service.join(roomId, memberId)
                        succeeded += memberId
                    } catch (e: LanglezException) {
                        rejected += e.status.value()
                    } finally {
                        done.countDown()
                    }
                }
            }

            ready.await()
            go.countDown()
            done.await()
        }

        Then("정원을 넘겨 들어가지 않는다") {
            sessions.participants(roomId).size shouldBe capacity
        }

        Then("예외 없이 돌아온 사람은 전부 실제 참여자다") {
            succeeded.forEach { sessions.isParticipant(roomId, it) shouldBe true }
        }

        Then("들어가지 못한 쪽은 409 로 실패를 안다") {
            rejected.size shouldBe contenders.size - capacity
            rejected.toSet() shouldContainExactly setOf(409)
        }
    }
})

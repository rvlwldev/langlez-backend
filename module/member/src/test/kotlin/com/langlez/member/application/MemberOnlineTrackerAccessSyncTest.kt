package com.langlez.member.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.member.domain.MemberRepository
import com.langlez.redis.config.RedissonConfiguration
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.testcontainers.containers.GenericContainer

/**
 * 접속 정보 동기화 및 원자적 RENAME(TOCTOU 방지) 검증 테스트 (C-13).
 */
class MemberOnlineTrackerAccessSyncTest : BehaviorSpec({

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

    val repo = mockk<MemberRepository>(relaxed = true)
    val tracker = MemberOnlineTracker(redisson, repo)

    afterSpec {
        redisson.shutdown()
        redis.stop()
    }

    Given("회원이 접속 기록(IP, deviceId)을 남기면") {
        clearMocks(repo)
        val memberId = 100L
        tracker.recordAccess(memberId, "192.168.1.10", "device-pixel-1")

        When("syncAccessInfo 가 실행되면") {
            tracker.syncAccessInfo()

            Then("DB에 IP와 기기 정보가 반영된다") {
                verify(exactly = 1) {
                    repo.updateAccessInfo(
                        id = memberId,
                        accessedAt = any(),
                        ip = "192.168.1.10",
                        deviceId = "device-pixel-1"
                    )
                }
            }

            Then("레디스의 임시 및 처리 키가 모두 정리된다") {
                redisson.getMap<String, String>("member:access:$memberId").isExists shouldBe false
                redisson.getMap<String, String>("member:access:$memberId:processing").isExists shouldBe false
            }
        }
    }

    Given("동기화 처리(RENAME) 직후에 새로운 접속이 발생하는 경우 (TOCTOU 방지 검증)") {
        clearMocks(repo)
        val memberId = 200L

        // 1. 기존 접속 기록
        tracker.recordAccess(memberId, "10.0.0.1", "device-old")

        // 2. 동기화 처리의 원자적 RENAME 시뮬레이션
        val accessKey = "member:access:$memberId"
        val processingKey = "$accessKey:processing"
        redisson.getMap<String, String>(accessKey).rename(processingKey)

        // 3. RENAME 직후 새 접속 발생 (원래 accessKey 에 새로 기록되고 dirty 에 등록됨)
        tracker.recordAccess(memberId, "10.0.0.2", "device-new")

        // 4. 이전 처리 루프의 processingKey 읽기 및 삭제
        val processingMap = redisson.getMap<String, String>(processingKey)
        val oldMeta = processingMap.readAllMap()
        processingMap.delete()

        Then("이전 주기에서 읽은 메타데이터는 device-old 이다") {
            oldMeta["device"] shouldBe "device-old"
            oldMeta["ip"] shouldBe "10.0.0.1"
        }

        Then("processingKey 가 삭제되어도 새로 들어온 accessKey 는 삭제되지 않고 보존된다") {
            val newMap = redisson.getMap<String, String>(accessKey)
            newMap.isExists shouldBe true
            newMap.readAllMap() shouldBe mapOf("ip" to "10.0.0.2", "device" to "device-new")
            redisson.getSet<Long>("member:access:dirty").contains(memberId) shouldBe true
        }

        When("다음 주기의 syncAccessInfo 가 실행되면") {
            tracker.syncAccessInfo()

            Then("새 접속 정보가 정상적으로 DB에 반영된다") {
                verify(exactly = 1) {
                    repo.updateAccessInfo(
                        id = memberId,
                        accessedAt = any(),
                        ip = "10.0.0.2",
                        deviceId = "device-new"
                    )
                }
                redisson.getMap<String, String>(accessKey).isExists shouldBe false
            }
        }
    }

    Given("이전 크래시로 processing 키만 남아 있는 경우") {
        clearMocks(repo)
        val memberId = 300L
        val processingKey = "member:access:$memberId:processing"
        val processingMap = redisson.getMap<String, String>(processingKey)
        processingMap.put("ip", "172.16.0.1")
        processingMap.put("device", "device-crash-recovery")
        redisson.getSet<Long>("member:access:dirty").add(memberId)

        When("syncAccessInfo 가 실행되면") {
            tracker.syncAccessInfo()

            Then("남아 있던 processing 키의 정보가 복구되어 DB에 반영되고 키가 삭제된다") {
                verify(exactly = 1) {
                    repo.updateAccessInfo(
                        id = memberId,
                        accessedAt = any(),
                        ip = "172.16.0.1",
                        deviceId = "device-crash-recovery"
                    )
                }
                processingMap.isExists shouldBe false
            }
        }
    }
})

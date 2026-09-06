package com.langlez.profile.application

import com.langlez.exception.LanglezException
import com.langlez.profile.domain.ProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ProfileImageConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var profileService: ProfileService

    @Autowired
    lateinit var profileRepository: ProfileRepository

    @Autowired
    lateinit var redissonClient: RedissonClient

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7.0")
            .withExposedPorts(6379)
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    init {
        // member_image_urls 는 members 로의 FK 가 없다. 이미지 개수 제한만 보는 테스트라
        // 회원 행을 만들지 않고 id 만 쓴다 (profile 모듈은 member 모듈에 의존하지 않는다).
        Given("회원이 가입되어 있을 때") {
            val memberId = 90_001L

            // 다른 memberId 로 한 번 미리 호출해 Hibernate 쿼리 컴파일·AOP 프록시 초기화·Redis 연결의
            // 콜드 스타트 지연을 흡수한다. 이게 없으면 첫 스레드의 트랜잭션이 비정상적으로 오래 걸려
            // 나머지 스레드가 waitMs*retries(2초) 안에 락을 못 잡고 전부 실패하는 게 관측됐다 —
            // 락 정합성과 무관한 테스트 환경의 콜드 스타트 문제라 프로덕션 타임아웃 값을 건드리지 않는다.
            profileService.confirmAdditionalImage(90_000_001L, "profiles/warmup.jpg")

            When("10개의 스레드에서 동시에 confirmAdditionalImage를 호출하면") {
                val threadCount = 10
                val executor = Executors.newFixedThreadPool(threadCount)
                val startLatch = CountDownLatch(1)
                val doneLatch = CountDownLatch(threadCount)
                val exceptions = mutableListOf<Throwable>()

                (1..threadCount).forEach { index ->
                    executor.submit(Runnable {
                        try {
                            startLatch.await()
                            profileService.confirmAdditionalImage(memberId, "profiles/img_$index.jpg")
                        } catch (e: Throwable) {
                            synchronized(exceptions) {
                                exceptions.add(e)
                            }
                        } finally {
                            doneLatch.countDown()
                        }
                    })
                }

                startLatch.countDown()
                val completed = doneLatch.await(10, TimeUnit.SECONDS)
                executor.shutdown()

                Then("동작이 성공적으로 완료되고, 최종 이미지 개수는 6개를 초과하지 않아야 한다") {
                    completed shouldBe true

                    val totalImages = profileRepository.countImages(memberId)
                    totalImages shouldBe 6L

                    val limitExceededExceptions = synchronized(exceptions) {
                        exceptions.filter { it is LanglezException && it.status.value() == 400 }
                    }
                    limitExceededExceptions.size shouldBe (threadCount - 6)
                }
            }
        }

        // 리뷰(Important 1): mockk 로 IllegalStateException 을 손으로 스터빙하는 것만으로는
        // throwOnFailure=true 자체를 지워도 초록불이 나왔다 — ProfileImageLocker 가 실제로
        // 락 실패 시 null 이 아니라 예외를 던지는지는 아무도 검증하지 않았다. 여기서는 진짜
        // RedissonClient 로 락을 선점해 재시도 윈도우(waitMs*retries = 2초)를 실제로 소진시킨다.
        Given("다른 스레드가 같은 회원의 락을 이미 쥐고 있을 때") {
            val memberId = 90_002L
            val lockName = "lock:profile-image:$memberId"

            When("확정 요청이 재시도 윈도우 안에 락을 못 잡으면") {
                val acquiredLatch = CountDownLatch(1)
                val releaseLatch = CountDownLatch(1)
                val holder = thread {
                    val lock = redissonClient.getLock(lockName)
                    lock.lock()
                    acquiredLatch.countDown()
                    releaseLatch.await()
                    lock.unlock()
                }
                acquiredLatch.await()

                Then("NPE(500) 가 아니라 IllegalStateException 을 거쳐 409 CONFLICT 로 떨어진다") {
                    val ex = shouldThrow<LanglezException> {
                        profileService.confirmAdditionalImage(memberId, "profiles/blocked.jpg")
                    }
                    ex.status.value() shouldBe 409
                }

                releaseLatch.countDown()
                holder.join()
            }
        }

        // 리뷰(Important 2): confirmRepresentImage 는 [기존 대표 조회 → represent=false 저장 →
        // countImages 정원 체크 → 새 대표 represent=true 저장] 의 복합 상태 전이라 대표 사진
        // 더블클릭 같은 동시 호출에서 깨질 여지가 있었다. 이전에는 confirmAdditionalImage 만
        // 실 스레드로 검증했고 대표 사진 경로는 MockK 순차 호출뿐이었다.
        Given("대표 사진 확정에 여러 스레드가 동시에 몰릴 때") {
            val memberId = 90_003L

            // 콜드 스타트 워밍업. 위 confirmAdditionalImage 워밍업과 같은 이유.
            profileService.confirmRepresentImage(90_000_002L, "profiles/warmup-represent.jpg")

            When("10개의 스레드에서 동시에 confirmRepresentImage를 호출하면") {
                val threadCount = 10
                val executor = Executors.newFixedThreadPool(threadCount)
                val startLatch = CountDownLatch(1)
                val doneLatch = CountDownLatch(threadCount)
                val exceptions = mutableListOf<Throwable>()

                (1..threadCount).forEach { index ->
                    executor.submit(Runnable {
                        try {
                            startLatch.await()
                            profileService.confirmRepresentImage(memberId, "profiles/represent_$index.jpg")
                        } catch (e: Throwable) {
                            synchronized(exceptions) {
                                exceptions.add(e)
                            }
                        } finally {
                            doneLatch.countDown()
                        }
                    })
                }

                startLatch.countDown()
                val completed = doneLatch.await(10, TimeUnit.SECONDS)
                executor.shutdown()

                Then("최종 이미지 개수는 정원(6장)을 넘지 않고 대표 사진은 정확히 1장이다") {
                    completed shouldBe true

                    val totalImages = profileRepository.countImages(memberId)
                    totalImages shouldBe 6L

                    // represent=true 가 2장 이상이면 findRepresentImage 가 단건 조회라 그 자체로 터진다.
                    profileRepository.findRepresentImage(memberId) shouldNotBe null

                    val limitExceededExceptions = synchronized(exceptions) {
                        exceptions.filter { it is LanglezException && it.status.value() == 400 }
                    }
                    limitExceededExceptions.size shouldBe (threadCount - 6)
                }
            }
        }
    }
}

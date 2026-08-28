package com.langlez.profile.application

import com.langlez.exception.LanglezException
import com.langlez.profile.domain.ProfileRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
    }
}

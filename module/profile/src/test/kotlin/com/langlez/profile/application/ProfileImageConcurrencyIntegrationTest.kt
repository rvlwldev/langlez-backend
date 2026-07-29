package com.langlez.profile.application

import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRole
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
import org.testcontainers.containers.MySQLContainer
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ProfileImageConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var profileService: ProfileService

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var profileRepository: ProfileRepository

    companion object {
        @JvmField
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
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
            registry.add("spring.datasource.url") { mysql.jdbcUrl + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    init {
        Given("회원이 가입되어 있을 때") {
            val member = memberRepository.save(
                Member(
                    email = "lockuser@example.com",
                    username = "lockuser",
                    nickname = "LockUser",
                    provider = MemberProvider.GOOGLE,
                    providerId = "p-lockuser",
                    providerDisplayName = "LockUser",
                    role = MemberRole.MEMBER
                )
            )

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
                            profileService.confirmAdditionalImage(member.id, "https://cdn/profiles/img_$index.jpg")
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

                    val totalImages = profileRepository.countImages(member.id)
                    totalImages shouldBe 6L

                    val limitExceededExceptions = synchronized(exceptions) {
                        exceptions.filter { it is LanglezException && it.status == 400 }
                    }
                    limitExceededExceptions.size shouldBe (threadCount - 6)
                }
            }
        }
    }
}

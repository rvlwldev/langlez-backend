package com.langlez.matching.application

import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.relationship.application.RelationshipService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * systematic-debugging Phase 1 재현: 두 스레드가 동시에 같은 follow 관계를 생성하려 할 때
 * RelationshipService.follow()의 check-then-act + DataIntegrityViolationException catch가
 * 실제로 500(UnexpectedRollbackException 등)을 막아주는지, 진짜 PostgreSQL로 검증한다.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class RelationshipRaceConditionIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var relationshipService: RelationshipService

    @Autowired
    lateinit var memberRepository: MemberRepository

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

        @JvmField
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:6.0")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    private fun createMember(username: String): Member = memberRepository.save(
        Member(
            email = "$username@test.com",
            username = username,
            nickname = username,
            provider = MemberProvider.GOOGLE,
            providerId = "p-$username",
            providerDisplayName = username,
        )
    )

    init {
        Given("두 스레드가 동시에 같은 follow 관계를 생성하려 하면") {
            val follower = createMember("racer_follower")
            val followed = createMember("racer_followed")

            val ready = CountDownLatch(2)
            val go = CountDownLatch(1)
            val errors = Collections.synchronizedList(mutableListOf<Throwable>())
            val executor = Executors.newFixedThreadPool(2)

            val tasks = (1..2).map {
                executor.submit {
                    ready.countDown()
                    go.await()
                    try {
                        relationshipService.follow(follower.id, followed.id)
                    } catch (e: Throwable) {
                        errors.add(e)
                    }
                }
            }
            ready.await()
            go.countDown()
            tasks.forEach { it.get(15, TimeUnit.SECONDS) }
            executor.shutdown()

            Then("500(UnexpectedRollbackException/TransactionSystemException)이 발생하지 않아야 한다") {
                errors.forEach { println("[RACE-REPRO] ${it::class.qualifiedName}: ${it.message}") }
                errors.shouldBeEmpty()
            }
        }
    }
}

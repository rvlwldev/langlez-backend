package com.langlez.matching.application

import com.langlez.matching.api.MatchingResponse
import com.langlez.matching.domain.MatchingQueueRepository
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer

/** Redis ZSET("matching:queue") 기반 실시간 매칭이 실제 컨테이너 환경에서도 동작하는지 검증한다. */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
    ]
)
class MatchingQueueIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var matchingService: MatchingService

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var profileRepository: ProfileRepository

    @Autowired
    lateinit var queueRepository: MatchingQueueRepository

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

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

        @JvmField
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:6.0")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    private fun createMemberWithProfile(
        username: String,
        languageLevel: Profile.LanguageLevel,
        interests: Set<String>,
    ): Member =
        // member 저장과 profile 저장을 같은 트랜잭션(같은 영속성 컨텍스트)에서 수행해야
        // Profile의 @MapsId 연관관계가 detached entity 오류 없이 정상적으로 저장된다.
        transactionTemplate.execute {
            val member = memberRepository.save(
                Member(
                    email = "$username@example.com",
                    username = username,
                    nickname = username,
                    provider = Member.Provider.GOOGLE,
                    providerId = "p-$username",
                    providerDisplayName = username,
                    role = Member.Role.MEMBER,
                )
            )
            profileRepository.saveProfile(
                Profile(id = member.id, member = member, languageLevel = languageLevel, interests = interests.toMutableSet())
            )
            member
        }!!

    init {
        Given("같은 언어 레벨의 두 유저가 있을 때") {
            val alice = createMemberWithProfile("alice", Profile.LanguageLevel.INTERMEDIATE, setOf("movie", "music"))
            val bob = createMemberWithProfile("bob", Profile.LanguageLevel.INTERMEDIATE, setOf("movie"))

            When("alice가 먼저 큐에 참가하면") {
                val aliceResult = matchingService.joinQueue(alice.id)

                Then("매칭 후보가 없어 대기 상태가 된다") {
                    aliceResult.status shouldBe MatchingResponse.QueueStatus.Status.WAITING
                    queueRepository.isQueued(alice.id) shouldBe true
                }

                When("이어서 bob이 큐에 참가하면") {
                    val bobResult = matchingService.joinQueue(bob.id)

                    Then("즉시 alice와 매칭되고 둘 다 큐에서 빠진다") {
                        bobResult.status shouldBe MatchingResponse.QueueStatus.Status.MATCHED
                        bobResult.roomId shouldNotBe null

                        queueRepository.isQueued(alice.id) shouldBe false
                        queueRepository.isQueued(bob.id) shouldBe false
                    }
                }
            }
        }
    }
}

package com.langlez.chat.application

import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRole
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
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
class ChatRoomConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var chatService: ChatService

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var chatRoomRepository: ChatRoomRepository

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

    init {
        Given("두 명의 회원이 가입되어 있을 때") {
            val memberA = memberRepository.save(
                Member(
                    email = "alice@example.com",
                    username = "alice",
                    nickname = "Alice",
                    provider = MemberProvider.GOOGLE,
                    providerId = "p-alice",
                    providerDisplayName = "Alice",
                    role = MemberRole.MEMBER
                )
            )

            val memberB = memberRepository.save(
                Member(
                    email = "bob@example.com",
                    username = "bob",
                    nickname = "Bob",
                    provider = MemberProvider.GOOGLE,
                    providerId = "p-bob",
                    providerDisplayName = "Bob",
                    role = MemberRole.MEMBER
                )
            )

            When("10개의 스레드에서 동시에 getOrCreateRoom을 호출하면") {
                val threadCount = 10
                val executor = Executors.newFixedThreadPool(threadCount)
                val startLatch = CountDownLatch(1)
                val doneLatch = CountDownLatch(threadCount)
                val exceptions = mutableListOf<Throwable>()

                (1..threadCount).map {
                    executor.submit(Runnable {
                        try {
                            startLatch.await()
                            chatService.getOrCreateRoom(memberA.id, memberB.username)
                        } catch (e: Throwable) {
                            synchronized(exceptions) {
                                exceptions.add(e)
                            }
                        } finally {
                            doneLatch.countDown()
                        }
                    })
                }

                // Start concurrent execution
                startLatch.countDown()
                val completed = doneLatch.await(10, TimeUnit.SECONDS)
                executor.shutdown()

                Then("동작이 성공적으로 완료되고, 하나의 채팅방만 생성되어야 한다") {
                    completed shouldBe true
                    
                    val unexpectedExceptions = synchronized(exceptions) {
                        exceptions.filter { it !is LanglezException || it.status != 429 }
                    }
                    val rateLimitExceptions = synchronized(exceptions) {
                        exceptions.filter { it is LanglezException && it.status == 429 }
                    }

                    unexpectedExceptions shouldBe emptyList<Throwable>()
                    rateLimitExceptions.size shouldBe 0
                    
                    val roomDirect = chatRoomRepository.findByParticipants(memberA.id, memberB.id)
                    roomDirect shouldNotBe null
                    
                    val roomsForA = chatRoomRepository.findByParticipant(memberA.id, null, 100)
                    roomsForA.size shouldBe 1
                }
            }
        }
    }
}

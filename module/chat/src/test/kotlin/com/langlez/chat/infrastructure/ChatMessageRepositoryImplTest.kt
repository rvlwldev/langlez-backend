package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.time.Instant
import java.time.temporal.ChronoUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ChatMessageRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var chatMessageRepository: ChatMessageRepository

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

    init {
        Given("ChatMessageRepository 가 주어졌을 때") {
            val roomId = "room-123"

            When("메시지를 저장하고 조회할 때") {
                val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val m1 = chatMessageRepository.save(ChatMessage(roomId = roomId, senderId = 1L, type = ChatMessage.Type.TEXT, content = "Hi", createdAt = now.minusSeconds(10)))
                val m2 = chatMessageRepository.save(ChatMessage(roomId = roomId, senderId = 2L, type = ChatMessage.Type.TEXT, content = "Hello", createdAt = now.minusSeconds(5)))
                val m3 = chatMessageRepository.save(ChatMessage(roomId = roomId, senderId = 1L, type = ChatMessage.Type.TEXT, content = "What's up?", createdAt = now))

                Then("createdAt 내림차순(최신순) 정렬 및 cursor 기반 페이지네이션이 정확히 동작해야 한다") {
                    val page1 = chatMessageRepository.findByRoom(roomId, null, 2)
                    page1 shouldHaveSize 2
                    page1[0].id shouldBe m3.id
                    page1[1].id shouldBe m2.id

                    val page2 = chatMessageRepository.findByRoom(roomId, page1.last().id, 2)
                    page2 shouldHaveSize 1
                    page2[0].id shouldBe m1.id
                }
            }

            When("안읽은 메시지 개수를 계산할 때") {
                val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                // m1: 1L이 보냄 -> 1L에게는 카운트 제외, 타인에게는 포함 가능
                // m2: 2L이 보냄 -> 2L에게는 카운트 제외, 타인에게는 포함 가능
                // m3: 1L이 보냄 -> 1L에게는 카운트 제외
                val m1 = chatMessageRepository.save(ChatMessage(roomId = "room-unread", senderId = 1L, type = ChatMessage.Type.TEXT, content = "1", createdAt = now.minusSeconds(10)))
                val m2 = chatMessageRepository.save(ChatMessage(roomId = "room-unread", senderId = 2L, type = ChatMessage.Type.TEXT, content = "2", createdAt = now.minusSeconds(5)))
                val m3 = chatMessageRepository.save(ChatMessage(roomId = "room-unread", senderId = 1L, type = ChatMessage.Type.TEXT, content = "3", createdAt = now))

                Then("특정 시각 이후의 메시지 중 본인이 보낸 게 아닌 것만 카운트되어야 한다") {
                    // 1L의 관점에서, now.minusSeconds(12) 이후 안 읽은 메시지 개수: m2만 세어져서 1이어야 함. (m1, m3는 본인이 보냄)
                    chatMessageRepository.countUnread("room-unread", 1L, now.minusSeconds(12)) shouldBe 1L

                    // 2L의 관점에서, now.minusSeconds(12) 이후 안 읽은 메시지 개수: m1, m3만 세어져서 2여야 함. (m2는 본인이 보냄)
                    chatMessageRepository.countUnread("room-unread", 2L, now.minusSeconds(12)) shouldBe 2L

                    // 2L의 관점에서, now.minusSeconds(7) 이후 안 읽은 메시지 개수: m3만 세어져서 1이어야 함.
                    chatMessageRepository.countUnread("room-unread", 2L, now.minusSeconds(7)) shouldBe 1L
                }
            }
        }
    }
}

package com.langlez.chat.application

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * A-05: `listMessages` 가 Mongo 조회 시점에 Postgres 트랜잭션을 쥐고 있지 않은지 본다.
 *
 * mockk 로는 실제 `@Transactional` AOP 프록시가 안 걸려 이 경계를 못 잡는다. 그래서 실제
 * 트랜잭션 매니저를 올리고, `ChatMessageRepository.findByRoom` 을 부르는 순간
 * `TransactionSynchronizationManager.isActualTransactionActive()` 를 기록하는 스파이로 감싼다.
 * 수정 전 코드(`@Transactional(readOnly = true)` 가 메서드 전체를 감쌈)에서는 이 값이 `true` 였다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class ChatServiceTransactionBoundaryIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: ChatService

    @Autowired
    lateinit var repo: ChatRepository

    @Autowired
    lateinit var recordingMessages: RecordingChatMessageRepository

    @TestConfiguration
    class Config {
        // ChatMessageRepositoryImpl 은 구체 타입으로 단 하나뿐이라 모호함 없이 주입된다.
        @Bean
        @Primary
        fun recordingChatMessageRepository(
            delegate: com.langlez.chat.infrastructure.mongo.ChatMessageRepositoryImpl
        ): RecordingChatMessageRepository = RecordingChatMessageRepository(delegate)
    }

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @JvmField
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .also { it.start() }

        @JvmField
        val mongo: MongoDBContainer = MongoDBContainer("mongo:6.0").also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
        }
    }

    init {
        Given("방 참여자가 메시지 목록을 조회하면") {
            val (me, partner) = 8001L to 8002L
            val room = repo.createRoom(me, partner)
            val seq = recordingMessages.nextSeq(room.id)
            recordingMessages.save(ChatMessage(room.id, partner, seq, ChatMessage.Type.TEXT, "hi"))
            recordingMessages.txActiveOnFindByRoom.clear()

            service.listMessages(me, room.id, 10, null)

            Then("Mongo 조회 시점에는 Postgres 트랜잭션이 열려 있지 않다") {
                recordingMessages.txActiveOnFindByRoom shouldBe listOf(false)
            }
        }
    }
}

class RecordingChatMessageRepository(
    private val delegate: ChatMessageRepository,
) : ChatMessageRepository by delegate {

    val txActiveOnFindByRoom = mutableListOf<Boolean>()

    override fun findByRoom(roomId: Long, size: Int, cursor: Long?): List<ChatMessage> {
        txActiveOnFindByRoom.add(TransactionSynchronizationManager.isActualTransactionActive())
        return delegate.findByRoom(roomId, size, cursor)
    }
}

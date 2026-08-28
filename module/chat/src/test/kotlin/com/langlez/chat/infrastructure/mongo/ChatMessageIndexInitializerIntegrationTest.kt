package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainAll
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * `auto-index-creation` 을 끈 상태에서 `ChatMessageIndexInitializer` 가 그 자리를 실제로 채우는지 고정한다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.mongodb.auto-index-creation=false",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class ChatMessageIndexInitializerIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    internal lateinit var initializer: ChatMessageIndexInitializer

    @Autowired
    lateinit var template: MongoTemplate

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

        // MongoDBContainer 는 단일 노드 레플리카셋으로 뜬다.
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
        Given("auto-index-creation 이 꺼진 상태에서 초기화기를 직접 돌리면") {
            initializer.ensureIndexes()

            Then("ChatMessage 에 정의된 인덱스 4개가 모두 만들어진다") {
                val names = template.indexOps(ChatMessage::class.java).indexInfo.map { it.name }

                names shouldContainAll listOf(
                    "IDX_CHAT_MESSAGE_ROOM_SEQ",
                    "IDX_CHAT_MESSAGE_ROOM_CREATED",
                    "IDX_CHAT_MESSAGE_CREATED",
                    "IDX_CHAT_MESSAGE_UNPUBLISHED",
                )
            }
        }
    }
}

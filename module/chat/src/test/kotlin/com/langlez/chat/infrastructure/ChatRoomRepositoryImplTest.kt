package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
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
class ChatRoomRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var chatRoomRepository: ChatRoomRepository

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
        Given("ChatRoomRepository 가 주어졌을 때") {

            When("두 참여자의 방을 찾을 때") {
                val room = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 2L)))

                Then("참여자 ID 순서를 뒤바꿔 호출해도 동일한 방이 반환되어야 한다") {
                    val found1 = chatRoomRepository.findByParticipants(1L, 2L)
                    found1 shouldNotBe null
                    found1!!.id shouldBe room.id

                    val found2 = chatRoomRepository.findByParticipants(2L, 1L)
                    found2 shouldNotBe null
                    found2!!.id shouldBe room.id
                }
            }

            When("특정 참여자의 방 목록을 커서 페이지네이션으로 조회할 때") {
                val now = Instant.now().truncatedTo(ChronoUnit.MILLIS)
                val room1 = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 2L), lastMessageAt = now.minusSeconds(10)))
                val room2 = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 3L), lastMessageAt = now.minusSeconds(5)))
                val room3 = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 4L), lastMessageAt = now))
                val room4 = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 5L), lastMessageAt = null))

                Then("lastMessageAt 내림차순(최신순) 정렬 및 null 인 방이 마지막에 페이지네이션 되어야 한다") {
                    val page1 = chatRoomRepository.findByParticipant(1L, null, 2)
                    page1 shouldHaveSize 2
                    page1[0].id shouldBe room3.id
                    page1[1].id shouldBe room2.id

                    val page2 = chatRoomRepository.findByParticipant(1L, page1.last().id, 2)
                    page2 shouldHaveSize 2
                    page2[0].id shouldBe room1.id
                    page2[1].id shouldBe room4.id
                }
            }

            When("읽음 상태를 갱신할 때") {
                val room = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 2L)))
                val readAt = Instant.now().truncatedTo(ChronoUnit.MILLIS)

                chatRoomRepository.updateReadStatus(room.id!!, 1L, readAt)

                Then("해당 방의 readStatus 에 반영되어야 한다") {
                    val updated = chatRoomRepository.findById(room.id!!)
                    updated shouldNotBe null
                    updated!!.readStatus[1L] shouldBe readAt
                }
            }
        }
    }
}

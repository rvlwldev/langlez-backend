package com.langlez.chat.application

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 이중 쓰기 대사 검증.
 *
 * 저장소가 둘이라 "Mongo 에는 메시지가 들어갔는데 Postgres 방 메타는 못 쓴" 상태가 실제로 생긴다.
 * 그 상태를 직접 만들어 두고(= ChatService 를 거치지 않고 Mongo 에만 쓴다) 대사가 메우는지 본다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.data.mongodb.auto-index-creation=true",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ChatReconcilerTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    internal lateinit var reconciler: ChatReconciler

    @Autowired
    lateinit var repo: ChatRepository

    @Autowired
    lateinit var messages: ChatMessageRepository

    @Autowired
    lateinit var tx: TransactionTemplate

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
        // Mongo 는 밀리초까지만 담는다. 저장 전후 비교가 나노초 때문에 어긋나지 않게 맞춘다.
        // 대사 대상은 최근 창(30분) 안의 메시지라 전부 지금 기준 몇 분 안쪽으로 만든다.
        val base = Instant.now().truncatedTo(ChronoUnit.MILLIS)

        // ChatService 를 거치지 않는다 — Postgres 갱신 직전에 죽은 상태가 정확히 이 모양이다.
        fun sendToMongoOnly(roomId: Long, senderId: Long, content: String, at: Instant) = messages.save(
            ChatMessage(
                roomId = roomId,
                senderId = senderId,
                seq = messages.nextSeq(roomId),
                type = ChatMessage.Type.TEXT,
                content = content,
                createdAt = at,
            )
        )

        Given("메시지는 Mongo 에 들어갔는데 Postgres 방 메타 갱신이 끊기면") {
            val (sender, receiver) = 3001L to 3002L
            val room = repo.createRoom(sender, receiver)
            sendToMongoOnly(room.id, sender, "안녕", base.minusSeconds(120))
            sendToMongoOnly(room.id, sender, "거기 있어?", base.minusSeconds(60))

            Then("프리뷰와 마지막 시각이 마지막 메시지로 맞춰진다") {
                reconciler.reconcile()

                val found = repo.findRoom(room.id)!!
                found.lastMessagePreview shouldBe "거기 있어?"
                found.lastMessageAt shouldBe base.minusSeconds(60)
            }

            Then("받는 쪽 안 읽은 수만 채워진다 (자기가 보낸 건 안 읽은 게 아니다)") {
                reconciler.reconcile()

                repo.findParticipant(room.id, receiver)!!.unreadCount shouldBe 2
                repo.findParticipant(room.id, sender)!!.unreadCount shouldBe 0
            }

            Then("몇 번을 다시 계산해도 값이 부풀지 않는다 (멱등)") {
                // 첫 실행이 프리뷰를 맞춰 두면 다음 실행은 "이미 일관됨"으로 걸러진다.
                // 그 방어를 일부러 걷어내고 계산 자체가 멱등한지 본다 —
                // 락이 풀린 사이 두 인스턴스가 같은 방을 겹쳐 도는 상황이 실제로 이 모양이다.
                repeat(3) {
                    tx.executeWithoutResult {
                        repo.findRoom(room.id)!!.onMessage("낡은 프리뷰", base.minusSeconds(300))
                    }

                    reconciler.reconcile()

                    // 놓친 만큼 더하는 방식이면 여기서 2 → 4 → 6 으로 불어난다.
                    repo.findParticipant(room.id, receiver)!!.unreadCount shouldBe 2
                    repo.findParticipant(room.id, sender)!!.unreadCount shouldBe 0
                    repo.findRoom(room.id)!!.lastMessagePreview shouldBe "거기 있어?"
                    repo.findRoom(room.id)!!.lastMessageAt shouldBe base.minusSeconds(60)
                }
            }
        }

        Given("한쪽이 중간까지 읽은 뒤 나머지가 반영되지 않으면") {
            val (sender, receiver) = 3005L to 3006L
            val room = repo.createRoom(sender, receiver)
            val read = sendToMongoOnly(room.id, sender, "1", base.minusSeconds(50))
            sendToMongoOnly(room.id, sender, "2", base.minusSeconds(40))
            sendToMongoOnly(room.id, sender, "3", base.minusSeconds(30))

            repo.saveParticipant(repo.findParticipant(room.id, receiver)!!.apply { markRead(read.createdAt) })

            Then("읽은 시각 이후 메시지만 센다") {
                reconciler.reconcile()

                repo.findParticipant(room.id, receiver)!!.unreadCount shouldBe 2
            }
        }

        Given("이미 두 저장소가 맞는 방은") {
            val (sender, receiver) = 3003L to 3004L
            val room = repo.createRoom(sender, receiver)
            val message = sendToMongoOnly(room.id, sender, "본문", base.minusSeconds(30))

            // 프리뷰·시각은 맞춰 두고 카운터만 일부러 틀린 값으로 둔다. 대사가 손댔는지 이 값으로 드러난다.
            // 방 갱신은 서비스와 같이 트랜잭션 안에서 더티 체킹으로 반영한다.
            tx.executeWithoutResult { repo.findRoom(room.id)!!.onMessage("서비스가 쓴 프리뷰", message.createdAt) }
            repo.saveParticipant(repo.findParticipant(room.id, receiver)!!.apply { syncUnread(7) })

            Then("대사가 아무것도 바꾸지 않는다") {
                reconciler.reconcile()

                repo.findRoom(room.id)!!.lastMessagePreview shouldBe "서비스가 쓴 프리뷰"
                repo.findParticipant(room.id, receiver)!!.unreadCount shouldBe 7
            }
        }

        Given("나간 참여자에게 새 메시지가 왔는데 rejoin 이 누락되면") {
            val room = repo.createRoom(940L, 941L)

            tx.executeWithoutResult {
                repo.findParticipant(room.id, 941L)!!.apply { leave(Instant.now().minusSeconds(60)) }
                    .also(repo::saveParticipant)
            }

            // 940 이 보냈지만 Postgres 갱신이 통째로 누락된 상태(이중 쓰기 창)
            messages.save(
                ChatMessage(
                    roomId = room.id,
                    senderId = 940L,
                    seq = messages.nextSeq(room.id),
                    type = ChatMessage.Type.TEXT,
                    content = "돌아와",
                )
            )

            reconciler.reconcile()

            Then("방이 되살아난다") {
                // 되살리지 않으면 941 의 목록에서 이 방이 계속 안 보여 메시지가 통째로 묻힌다
                repo.findParticipant(room.id, 941L)!!.leftAt shouldBe null
            }
        }
    }
}

package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
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

@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ChatRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: ChatRepository

    /** 방의 마지막 메시지 갱신은 서비스가 트랜잭션 안에서 더티 체킹으로 반영한다. 테스트도 같은 방식으로 쓴다. */
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

        // 메시지는 Mongo 에 있다. 이 테스트가 쓰진 않지만 chat 컨텍스트가 뜨려면 접속 대상이 있어야 한다.
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

        // Postgres timestamp(6) 은 마이크로초까지만 담는다. 커서 비교가 나노초 때문에 어긋나지 않게 맞춘다.
        private fun now() = Instant.now().truncatedTo(ChronoUnit.MICROS)
    }

    init {
        Given("두 사람 사이에 방을 만들면") {
            val (a, b) = 1001L to 1002L
            val room = repo.createRoom(a, b)

            Then("어느 순서로 찾아도 같은 방이 나온다") {
                repo.findRoomBetween(a, b)?.id shouldBe room.id
                repo.findRoomBetween(b, a)?.id shouldBe room.id
            }

            Then("두 참여자가 등록된다") {
                repo.findParticipants(room.id).map { it.memberId }.toSet() shouldBe setOf(a, b)
                repo.findParticipant(room.id, a).shouldNotBeNull()
            }

            Then("무관한 사람과의 방은 없다") {
                repo.findRoomBetween(a, 9999L) shouldBe null
            }
        }

        Given("방 목록을 조회하면") {
            val me = 1007L
            val older = repo.createRoom(me, 1008L)
            val newer = repo.createRoom(me, 1009L)

            val base = now()
            tx.executeWithoutResult { repo.findRoom(older.id)!!.onMessage("old", base.minusSeconds(60)) }
            tx.executeWithoutResult { repo.findRoom(newer.id)!!.onMessage("mine", base) }

            // 메시지가 Mongo 로 가면서 안 읽은 수는 조인으로 셀 수 없다. 받는 쪽 참여자 행의 카운터가 정답이다.
            repo.increaseUnread(older.id, me)
            repeat(2) { repo.increaseUnread(newer.id, me) }

            Then("마지막 메시지 최신순으로 상대 id 와 안 읽은 수가 함께 나온다") {
                val summaries = repo.findRoomSummaries(me, 10, null)
                summaries.map { it.room.id } shouldBe listOf(newer.id, older.id)
                summaries[0].partnerId shouldBe 1009L
                summaries[0].unreadCount shouldBe 2
                summaries[1].partnerId shouldBe 1008L
                summaries[1].unreadCount shouldBe 1
            }

            Then("읽음 처리하면 안 읽은 수가 0 이 된다") {
                repo.saveParticipant(repo.findParticipant(newer.id, me)!!.apply { markRead(now()) })

                repo.findRoomSummaries(me, 10, null).first { it.room.id == newer.id }.unreadCount shouldBe 0
            }

            Then("커서로 다음 장을 이어 받는다") {
                val first = repo.findRoomSummaries(me, 1, null)
                first shouldHaveSize 1
                first[0].room.id shouldBe newer.id

                val second = repo.findRoomSummaries(me, 1, first[0].room.lastMessageAt)
                second.map { it.room.id } shouldBe listOf(older.id)
            }

            Then("나간 방도 목록에 남는다 (재입장 정책)") {
                repo.saveParticipant(repo.findParticipant(older.id, me)!!.apply { leave(now()) })

                repo.findRoomSummaries(me, 10, null).map { it.room.id } shouldBe listOf(newer.id, older.id)
            }
        }

        Given("같은 방으로 메시지가 동시에 여러 건 들어오면") {
            val room = repo.createRoom(710L, 711L)

            When("안 읽은 수를 병렬로 올리면") {
                val threads = 20
                val pool = java.util.concurrent.Executors.newFixedThreadPool(threads)
                val start = java.util.concurrent.CountDownLatch(1)
                val done = java.util.concurrent.CountDownLatch(threads)

                repeat(threads) {
                    pool.submit {
                        start.await()
                        runCatching { repo.increaseUnread(room.id, 711L) }
                        done.countDown()
                    }
                }
                start.countDown()
                done.await(20, java.util.concurrent.TimeUnit.SECONDS)
                pool.shutdown()

                Then("증가분이 하나도 유실되지 않는다") {
                    // 엔티티에 읽고-쓰기로 올리면 마지막 쓰기만 남아 값이 20보다 작아진다
                    repo.findParticipant(room.id, 711L)!!.unreadCount shouldBe threads.toLong()
                }
            }
        }
    }
}

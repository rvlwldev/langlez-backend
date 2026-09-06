package com.langlez.chat.infrastructure

import com.langlez.chat.application.ChatService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * 같은 두 사람의 1:1 방을 동시에 열어도 방이 하나만 생기는지 본다.
 *
 * `ChatService.getOrCreateRoom` 의 `findRoomBetween` 검사는 check-then-act 라
 * 두 스레드가 같은 순간에 null 을 보면 둘 다 방을 만든다. 앱에서 못 막는 경합이고
 * **UNQ_CHAT_ROOM_PAIR(V19) 만이 최종 방어선이다.** 갈라진 방은 자동 복구가 없다 —
 * A 는 방 1에, B 는 방 2에 쓰고 서로의 메시지를 영영 못 본다.
 *
 * 유니크만 걸면 진 쪽이 500 을 보므로 "예외를 밖으로 내보내지 않는다"도 함께 고정한다.
 *
 * 행을 남기는 스펙이라 일반 CRUD 스펙(`ChatRepositoryImplTest`)과 파일을 나눈다.
 * 시작 시점을 맞추는 데 `Thread.sleep` 대신 `CyclicBarrier` 를 쓴다 —
 * 잠들었다 깨는 시각은 못 맞추지만 배리어는 마지막 스레드가 도달한 순간을 정확히 맞춘다.
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
class ChatRoomConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: ChatService

    @Autowired
    lateinit var dataSource: DataSource

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

        // 이 스펙은 메시지를 쓰지 않지만 chat 컨텍스트가 뜨려면 Mongo 접속 대상이 있어야 한다.
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

    /** 그 사람이 참여한 방 수. 방이 갈리면 2 가 된다. */
    private fun countRooms(memberId: Long): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("select count(*) from chat_room_members where member_id = ?").use { statement ->
            statement.setLong(1, memberId)
            statement.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    /**
     * 두 스레드가 같은 상대와의 방을 동시에 연다. 인자 순서를 서로 반대로 준다 —
     * 정규화가 빠져 있으면 (a,b) 와 (b,a) 가 다른 방으로 갈린다.
     */
    private fun openRoomsInParallel(a: Long, b: Long): Pair<List<Long>, List<Throwable>> {
        val barrier = CyclicBarrier(2)
        val roomIds = java.util.Collections.synchronizedList(mutableListOf<Long>())
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        val threads = listOf(a to b, b to a).map { (me, partner) ->
            Thread.ofVirtual().unstarted {
                barrier.await(10, TimeUnit.SECONDS)
                runCatching { service.getOrCreateRoom(me, partner).id }
                    .onSuccess(roomIds::add)
                    .onFailure(failures::add)
            }
        }

        threads.forEach(Thread::start)
        threads.forEach { it.join(30_000) }

        return roomIds to failures
    }

    init {
        Given("두 사람이 서로에게 동시에 채팅을 걸면") {
            val (a, b) = 9001L to 9002L
            val (roomIds, failures) = openRoomsInParallel(a, b)

            Then("어느 쪽도 예외를 받지 않는다 (동시 요청은 정상 상황이다)") {
                failures shouldBe emptyList()
            }

            Then("둘 다 같은 방 id 를 받는다") {
                roomIds.toSet().size shouldBe 1
            }

            Then("방이 하나만 남는다") {
                countRooms(a) shouldBe 1
                countRooms(b) shouldBe 1
            }
        }

        Given("이미 방이 있는 두 사람이면") {
            val (a, b) = 9003L to 9004L
            val first = service.getOrCreateRoom(a, b)

            When("인자 순서를 바꿔 다시 열면") {
                val again = service.getOrCreateRoom(b, a)

                Then("같은 방이 나온다") {
                    again.id shouldBe first.id
                }

                Then("방이 새로 생기지 않는다") {
                    countRooms(a) shouldBe 1
                }
            }
        }
    }
}

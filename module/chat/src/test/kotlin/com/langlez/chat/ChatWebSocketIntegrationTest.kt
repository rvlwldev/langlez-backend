package com.langlez.chat

import com.langlez.core.MessageBroadcaster
import com.langlez.member.contract.MemberSuspendedEvent
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.security.TokenManager
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.lang.reflect.Type
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ExecutionException
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

/**
 * 실제 STOMP 클라이언트를 붙여 실시간 전달 경로 전체를 확인한다.
 * 빈이 뜨는지가 아니라 `MessageBroadcaster.broadcast` 가 구독자에게 실제로 닿는지를 본다
 * (레디스 발행 → 구독 → STOMP 푸시까지 한 번에 검증된다).
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ChatWebSocketIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var tokens: TokenManager

    @Autowired
    lateinit var broadcaster: MessageBroadcaster

    @Autowired
    lateinit var chatRepository: com.langlez.chat.domain.ChatRepository

    @Autowired
    lateinit var tracker: OnlineTracker

    // CONNECT 가 계정 상태를 보므로 접속하는 회원은 실제 행이 있어야 한다.
    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var publisher: ApplicationEventPublisher

    // 정지 리스너가 AFTER_COMMIT 이라 트랜잭션 없이 발행하면 아무 일도 일어나지 않는다.
    @Autowired
    lateinit var tx: TransactionTemplate

    private val client = WebSocketStompClient(StandardWebSocketClient())
        .apply { messageConverter = MappingJackson2MessageConverter() }

    private fun newMember(): Member = memberRepository.save(
        Member(
            email = "ws-${java.util.UUID.randomUUID()}@test.com",
            provider = Member.Provider.GOOGLE,
            providerId = java.util.UUID.randomUUID().toString(),
        )
    )

    private fun connect(token: String?): StompSession {
        val headers = StompHeaders()
        token?.let { headers.add("Authorization", "Bearer $it") }

        return client
            .connectAsync(
                "ws://localhost:$port/ws/chat",
                WebSocketHttpHeaders(),
                headers,
                object : StompSessionHandlerAdapter() {},
            )
            .get(5, TimeUnit.SECONDS)
    }

    /** 화면 상태만 볼 땐 페이로드가 필요 없다. */
    private fun discardingHandler() = object : StompFrameHandler {
        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
        override fun handleFrame(headers: StompHeaders, payload: Any?) = Unit
    }

    /** SUBSCRIBE 가 서버에 반영된 시점을 클라이언트가 알 수 없어, 받을 때까지 짧게 재발행한다. */
    private fun broadcastUntilReceived(topic: String, payload: Any, received: BlockingQueue<Any>): Any? {
        repeat(25) {
            broadcaster.broadcast(topic, payload)
            received.poll(200, TimeUnit.MILLISECONDS)?.let { frame -> return frame }
        }
        return null
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

        // 메시지 저장소가 Mongo 라 chat 컨텍스트가 뜨려면 접속 대상이 있어야 한다.
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
        Given("유효한 액세스 토큰으로 STOMP 연결하면") {
            // 접속 회원이 참여자인 방을 실제로 만든다. 구독 인가가 참여 여부를 보기 때문이다.
            val me = newMember()
            val myRoom = chatRepository.createRoom(me.id, 2L)
            val session = connect(tokens.issueAccessToken(me.id, "tester", "ROLE_USER"))

            Then("연결이 수립된다") {
                session.isConnected shouldBe true
            }

            When("구독한 토픽으로 브로드캐스트하면") {
                val topic = "/topic/chat/room/${myRoom.id}"
                val received = LinkedBlockingQueue<Any>()

                session.subscribe(topic, object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        payload?.let { received.offer(it) }
                    }
                })

                val frame = broadcastUntilReceived(topic, mapOf("content" to "안녕"), received)

                Then("구독자가 페이로드를 수신한다") {
                    frame shouldNotBe null
                    (frame as Map<*, *>)["content"] shouldBe "안녕"
                }
            }

            session.disconnect()
        }

        /**
         * CONNECT 검사만으로는 **새 연결만** 막힌다. 소켓은 재검증 지점이 없어 이미 붙어 있던
         * 세션이 무기한 살아남고, 정지된 회원이 상대 메시지를 계속 읽는다.
         *
         * 이벤트 발행 → AFTER_COMMIT 리스너 → 레디스 전파 → 소켓 종료까지 한 번에 확인한다.
         */
        Given("이미 붙어 있는 회원이 정지되면") {
            val target = newMember()
            val victim = connect(tokens.issueAccessToken(target.id, "victim", "ROLE_USER"))

            Then("연결이 먼저 살아 있다") {
                victim.isConnected shouldBe true
            }

            When("정지 이벤트가 커밋되면") {
                tx.execute { publisher.publishEvent(MemberSuspendedEvent(target.id)) }

                Then("열려 있던 세션이 끊긴다") {
                    eventually(5.seconds) { victim.isConnected shouldBe false }
                }
            }
        }

        Given("내가 참여하지 않은 방을 구독하려 하면") {
            val othersRoom = chatRepository.createRoom(8L, 9L)
            val received = LinkedBlockingQueue<Any>()

            // 구독이 거부되면 STOMP 세션이 끊긴다. 그 과정에서 나는 예외는 무시하고
            // "메시지를 못 받았다"는 사실만 본다.
            runCatching {
                val session = connect(tokens.issueAccessToken(newMember().id, "tester", "ROLE_USER"))
                session.subscribe(
                    "/topic/chat/room/${othersRoom.id}",
                    object : StompFrameHandler {
                        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
                        override fun handleFrame(headers: StompHeaders, payload: Any?) {
                            payload?.let { received.offer(it) }
                        }
                    },
                )
            }

            Then("그 방의 메시지를 받지 못한다") {
                // 인가 없이 CONNECT 만 검사하면 로그인한 아무나 남의 대화를 엿볼 수 있다
                runCatching {
                    broadcastUntilReceived("/topic/chat/room/${othersRoom.id}", mapOf("content" to "비밀"), received)
                }
                received.poll(500, TimeUnit.MILLISECONDS) shouldBe null
            }
        }

        Given("와일드카드로 전체 방을 구독하려 하면") {
            val othersRoom = chatRepository.createRoom(18L, 19L)
            val received = LinkedBlockingQueue<Any>()

            runCatching {
                val session = connect(tokens.issueAccessToken(newMember().id, "tester", "ROLE_USER"))
                session.subscribe(
                    "/topic/chat/room/*",
                    object : StompFrameHandler {
                        override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
                        override fun handleFrame(headers: StompHeaders, payload: Any?) {
                            payload?.let { received.offer(it) }
                        }
                    },
                )
            }

            Then("어떤 방의 메시지도 받지 못한다") {
                runCatching {
                    broadcastUntilReceived("/topic/chat/room/${othersRoom.id}", mapOf("content" to "비밀"), received)
                }
                received.poll(500, TimeUnit.MILLISECONDS) shouldBe null
            }
        }

        Given("채팅방을 구독하면") {
            val viewer = newMember().id
            val room = chatRepository.createRoom(viewer, 32L)
            val topic = "/topic/chat/room/${room.id}"
            // viewers 는 접속 여부와 교집합이라 실제 앱처럼 핑이 먼저 있어야 한다
            tracker.toOnline(viewer)
            val session = connect(tokens.issueAccessToken(viewer, "viewer", "ROLE_USER"))
            val subscription = session.subscribe(topic, discardingHandler())

            Then("그 방을 보고 있는 사람으로 기록된다") {
                eventually(3.seconds) { tracker.viewers(topic) shouldContain viewer }
            }

            When("구독을 해제하면") {
                subscription.unsubscribe()

                // UNSUBSCRIBE 프레임엔 목적지가 없다. 세션 속성에 기억해둔 게 실제로 풀리는지 본다.
                Then("보고 있는 사람에서 빠진다") {
                    eventually(3.seconds) { tracker.viewers(topic) shouldNotContain viewer }
                }
            }

            session.disconnect()
        }

        // 앱이 강제 종료되면 UNSUBSCRIBE 없이 소켓만 끊긴다. 이때 정리가 안 되면
        // 그 회원은 영원히 "그 방을 보는 중"이 되어 알림이 통째로 사라진다.
        Given("구독한 채로 연결이 끊기면") {
            val viewer = newMember().id
            val room = chatRepository.createRoom(viewer, 42L)
            val topic = "/topic/chat/room/${room.id}"
            // viewers 는 접속 여부와 교집합이라 실제 앱처럼 핑이 먼저 있어야 한다
            tracker.toOnline(viewer)
            val session = connect(tokens.issueAccessToken(viewer, "viewer", "ROLE_USER"))
            session.subscribe(topic, discardingHandler())

            eventually(3.seconds) { tracker.viewers(topic) shouldContain viewer }
            session.disconnect()

            Then("보던 방에서 모두 빠진다") {
                eventually(3.seconds) { tracker.viewers(topic) shouldNotContain viewer }
            }
        }

        // HTTP 는 매 요청 상태를 보는데 실시간 채널은 CONNECT 때 토큰만 보던 시절이 있었다.
        // 정지된 회원이 토큰 TTL(1시간) 내내 다시 붙어 상대 메시지를 읽을 수 있었다.
        Given("정지된 회원이 새로 연결하려 하면") {
            val banned = newMember().also {
                it.suspend()
                memberRepository.save(it)
            }

            Then("연결이 거부된다") {
                shouldThrow<ExecutionException> {
                    connect(tokens.issueAccessToken(banned.id, "banned", "ROLE_USER"))
                }
            }
        }

        Given("토큰 없이 STOMP 연결하면") {
            Then("연결이 거부된다") {
                shouldThrow<ExecutionException> { connect(null) }
            }
        }

        Given("액세스 토큰이 아닌 토큰으로 STOMP 연결하면") {
            Then("연결이 거부된다") {
                shouldThrow<ExecutionException> { connect(tokens.issueRefreshToken(1L, "tester", "ROLE_USER")) }
            }
        }
    }
}

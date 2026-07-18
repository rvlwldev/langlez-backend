package com.langlez.chat.config

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.chat.api.TypingEventPayload
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.security.util.JwtParser
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.messaging.converter.MappingJackson2MessageConverter
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompFrameHandler
import org.springframework.messaging.simp.stomp.StompHeaders
import org.springframework.messaging.simp.stomp.StompSession
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.socket.WebSocketHttpHeaders
import org.springframework.web.socket.client.standard.StandardWebSocketClient
import org.springframework.web.socket.messaging.WebSocketStompClient
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import java.lang.reflect.Type
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class ChatWebSocketIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jwtParser: JwtParser

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
        Given("유효한 JWT 토큰과 WebSocket 연결이 준비되었을 때") {
            val stompClient = WebSocketStompClient(StandardWebSocketClient())
            stompClient.messageConverter = MappingJackson2MessageConverter().apply {
                objectMapper.registerKotlinModule()
            }

            val wsUrl = "ws://localhost:$port/ws/chat"

            val aliceToken = jwtParser.createAccessToken(1L, "MEMBER")
            val aliceHeaders = StompHeaders()
            aliceHeaders.add("Authorization", "Bearer $aliceToken")

            val bobToken = jwtParser.createAccessToken(2L, "MEMBER")
            val bobHeaders = StompHeaders()
            bobHeaders.add("Authorization", "Bearer $bobToken")

            val malloryToken = jwtParser.createAccessToken(3L, "MEMBER")
            val malloryHeaders = StompHeaders()
            malloryHeaders.add("Authorization", "Bearer $malloryToken")

            val room = chatRoomRepository.save(ChatRoom(participantIds = listOf(1L, 2L)))
            val roomId = room.id!!

            When("Alice와 Bob이 STOMP 엔드포인트에 CONNECT하고 Bob이 타이핑 이벤트를 구독하면") {
                val sessionHandler = object : StompSessionHandlerAdapter() {
                    override fun handleException(session: StompSession, command: StompCommand?, headers: StompHeaders, payload: ByteArray, ex: Throwable) {
                        println("=== STOMP SESSION EXCEPTION: command=$command, headers=$headers, payload=${String(payload)}, error=$ex")
                    }
                    override fun handleTransportError(session: StompSession, ex: Throwable) {
                        println("=== STOMP TRANSPORT ERROR: error=$ex")
                    }
                }

                val aliceSession: StompSession = stompClient.connectAsync(
                    wsUrl,
                    WebSocketHttpHeaders(),
                    aliceHeaders,
                    sessionHandler
                ).get(10, TimeUnit.SECONDS)

                val bobSession: StompSession = stompClient.connectAsync(
                    wsUrl,
                    WebSocketHttpHeaders(),
                    bobHeaders,
                    sessionHandler
                ).get(10, TimeUnit.SECONDS)

                val receivedEvents = CopyOnWriteArrayList<TypingEventPayload>()
                val latch = CountDownLatch(1)

                bobSession.subscribe("/topic/chat/room/$roomId/typing", object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type {
                        return TypingEventPayload::class.java
                    }

                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        println("=== Bob StompFrameHandler received: headers=$headers, payload=$payload, type=${payload?.javaClass}")
                        if (payload is TypingEventPayload) {
                            receivedEvents.add(payload)
                            latch.countDown()
                        } else {
                            println("=== WARNING: payload is not TypingEventPayload!")
                        }
                    }
                })

                // Wait slightly for subscription to be processed
                TimeUnit.MILLISECONDS.sleep(500)

                Then("Alice가 타이핑 이벤트를 전송했을 때 Bob이 이를 정상적으로 수신한다") {
                    aliceSession.send("/app/chat/$roomId/typing", mapOf("dummy" to "payload"))

                    val received = latch.await(5, TimeUnit.SECONDS)
                    received shouldBe true
                    receivedEvents.size shouldBe 1
                    receivedEvents[0].memberId shouldBe 1L

                    aliceSession.disconnect()
                    bobSession.disconnect()
                }
            }

            When("참여자가 아닌 유저(Mallory)가 같은 방의 이벤트를 엿들으려 하면") {
                val silentHandler = object : StompSessionHandlerAdapter() {}

                val mallorySession: StompSession = stompClient.connectAsync(
                    wsUrl,
                    WebSocketHttpHeaders(),
                    malloryHeaders,
                    silentHandler
                ).get(10, TimeUnit.SECONDS)

                val aliceSession2: StompSession = stompClient.connectAsync(
                    wsUrl,
                    WebSocketHttpHeaders(),
                    aliceHeaders,
                    silentHandler
                ).get(10, TimeUnit.SECONDS)

                val malloryReceived = CopyOnWriteArrayList<TypingEventPayload>()

                mallorySession.subscribe("/topic/chat/room/$roomId/typing", object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type = TypingEventPayload::class.java
                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        if (payload is TypingEventPayload) malloryReceived.add(payload)
                    }
                })

                TimeUnit.MILLISECONDS.sleep(500)

                Then("Alice가 타이핑 이벤트를 보내도 Mallory는 아무것도 수신하지 못한다 (구독이 거부되었기 때문)") {
                    aliceSession2.send("/app/chat/$roomId/typing", mapOf("dummy" to "payload"))
                    TimeUnit.SECONDS.sleep(2)

                    malloryReceived.size shouldBe 0

                    aliceSession2.disconnect()
                    // Mallory의 세션은 인터셉터가 미참여자 구독을 거부하면서 서버 쪽에서 이미 강제 종료되었을 수 있다.
                    runCatching { mallorySession.disconnect() }
                }
            }
        }
    }
}

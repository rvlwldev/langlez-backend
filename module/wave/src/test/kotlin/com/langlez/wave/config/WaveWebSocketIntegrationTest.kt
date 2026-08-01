package com.langlez.wave.config

import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRole
import com.langlez.security.util.JwtParser
import com.langlez.wave.application.WaveChatBroadcastPayload
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.WaveViewerTracker
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
import org.testcontainers.containers.PostgreSQLContainer
import java.lang.reflect.Type
import java.time.Instant
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class WaveWebSocketIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var jwtParser: JwtParser

    @Autowired
    lateinit var waveRoomRepository: WaveRoomRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var viewerTracker: WaveViewerTracker

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

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    private fun silentHandler() = object : StompSessionHandlerAdapter() {}

    private fun errorTrackingHandler(latch: CountDownLatch) = object : StompSessionHandlerAdapter() {
        override fun handleException(session: StompSession, command: StompCommand?, headers: StompHeaders, payload: ByteArray, ex: Throwable) {
            latch.countDown()
        }

        override fun handleTransportError(session: StompSession, ex: Throwable) {
            latch.countDown()
        }
    }

    private fun createMember(role: MemberRole): Member {
        val member = Member(
            email = "wave-${System.nanoTime()}@example.com",
            nickname = "Wave User",
            provider = MemberProvider.GOOGLE,
            providerId = "wave-${System.nanoTime()}",
            providerDisplayName = "Wave User"
        )
        member.role = role
        return memberRepository.save(member)
    }

    init {
        Given("STOMP 클라이언트가 준비되었을 때") {
            val stompClient = WebSocketStompClient(StandardWebSocketClient())
            stompClient.messageConverter = MappingJackson2MessageConverter().apply {
                objectMapper.registerKotlinModule()
            }
            val wsUrl = "ws://localhost:$port/ws/wave"

            When("addViewerIfAllowed를 직접 호출하면") {
                val member = createMember(MemberRole.MEMBER)
                val room = waveRoomRepository.save(WaveRoom(broadcasterId = member.id))

                Then("정상적으로 뷰어로 등록되고 시청자 수가 1 증가해야 한다") {
                    val allowed = viewerTracker.addViewerIfAllowed(room.id, member.id, room.maxParticipants)
                    allowed shouldBe true
                    viewerTracker.isViewer(room.id, member.id) shouldBe true
                    viewerTracker.viewerCount(room.id) shouldBe 1L
                }
            }

            When("존재하지 않는 방을 구독하려 하면") {
                val stranger = createMember(MemberRole.MEMBER)
                val strangerToken = jwtParser.createAccessToken(stranger.id, stranger.role.name)
                val headers = StompHeaders().apply { add("Authorization", "Bearer $strangerToken") }

                val errorLatch = CountDownLatch(1)
                val session = stompClient.connectAsync(wsUrl, WebSocketHttpHeaders(), headers, errorTrackingHandler(errorLatch))
                    .get(10, TimeUnit.SECONDS)

                Then("구독이 거부된다 (서버가 에러 프레임을 보내거나 세션을 종료한다)") {
                    session.subscribe("/topic/wave/999999/signal", object : StompFrameHandler {
                        override fun getPayloadType(headers: StompHeaders): Type = Any::class.java
                        override fun handleFrame(headers: StompHeaders, payload: Any?) {}
                    })

                    val rejected = errorLatch.await(5, TimeUnit.SECONDS)
                    rejected shouldBe true

                    runCatching { session.disconnect() }
                }
            }

            When("이미 종료된 방을 구독하려 하면") {
                val broadcaster = createMember(MemberRole.PREMIUM)
                val endedRoom = waveRoomRepository.save(
                    WaveRoom(broadcasterId = broadcaster.id, endedAt = Instant.now())
                )

                val viewer = createMember(MemberRole.MEMBER)
                val viewerToken = jwtParser.createAccessToken(viewer.id, viewer.role.name)
                val headers = StompHeaders().apply { add("Authorization", "Bearer $viewerToken") }

                val errorLatch = CountDownLatch(1)
                val session = stompClient.connectAsync(wsUrl, WebSocketHttpHeaders(), headers, errorTrackingHandler(errorLatch))
                    .get(10, TimeUnit.SECONDS)

                Then("구독이 거부된다") {
                    session.subscribe("/topic/wave/${endedRoom.id}/signal", object : StompFrameHandler {
                        override fun getPayloadType(headers: StompHeaders): Type = Any::class.java
                        override fun handleFrame(headers: StompHeaders, payload: Any?) {}
                    })

                    val rejected = errorLatch.await(5, TimeUnit.SECONDS)
                    rejected shouldBe true

                    runCatching { session.disconnect() }
                }
            }

            When("진행 중인 방에서 시그널링 메시지를 주고받으면") {
                val broadcaster = createMember(MemberRole.PREMIUM)
                val room = waveRoomRepository.save(WaveRoom(broadcasterId = broadcaster.id))

                val alice = createMember(MemberRole.MEMBER)
                val bob = createMember(MemberRole.MEMBER)
                val aliceHeaders = StompHeaders().apply {
                    add("Authorization", "Bearer ${jwtParser.createAccessToken(alice.id, alice.role.name)}")
                }
                val bobHeaders = StompHeaders().apply {
                    add("Authorization", "Bearer ${jwtParser.createAccessToken(bob.id, bob.role.name)}")
                }

                val aliceSession = stompClient.connectAsync(wsUrl, WebSocketHttpHeaders(), aliceHeaders, silentHandler())
                    .get(10, TimeUnit.SECONDS)
                val bobSession = stompClient.connectAsync(wsUrl, WebSocketHttpHeaders(), bobHeaders, silentHandler())
                    .get(10, TimeUnit.SECONDS)

                val received = CopyOnWriteArrayList<Map<*, *>>()
                val latch = CountDownLatch(1)

                bobSession.subscribe("/topic/wave/${room.id}/signal", object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type = Map::class.java
                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        if (payload is Map<*, *>) {
                            received.add(payload)
                            latch.countDown()
                        }
                    }
                })
                TimeUnit.MILLISECONDS.sleep(500)

                Then("다른 참가자가 시그널 페이로드를 그대로 수신한다") {
                    aliceSession.send("/app/wave/${room.id}/signal", mapOf("type" to "offer", "sdp" to "v=0..."))

                    val ok = latch.await(5, TimeUnit.SECONDS)
                    ok shouldBe true
                    received[0]["type"] shouldBe "offer"

                    aliceSession.disconnect()
                    bobSession.disconnect()
                }
            }

            When("무료 회원이 30초 이내에 채팅을 두 번 보내면") {
                val broadcaster = createMember(MemberRole.PREMIUM)
                val room = waveRoomRepository.save(WaveRoom(broadcasterId = broadcaster.id))

                val freeMember = createMember(MemberRole.MEMBER)
                val freeHeaders = StompHeaders().apply {
                    add("Authorization", "Bearer ${jwtParser.createAccessToken(freeMember.id, freeMember.role.name)}")
                }
                val listenerHeaders = StompHeaders().apply {
                    add("Authorization", "Bearer ${jwtParser.createAccessToken(broadcaster.id, broadcaster.role.name)}")
                }

                val senderSession = stompClient.connectAsync(wsUrl, WebSocketHttpHeaders(), freeHeaders, silentHandler())
                    .get(10, TimeUnit.SECONDS)
                val listenerSession = stompClient.connectAsync(wsUrl, WebSocketHttpHeaders(), listenerHeaders, silentHandler())
                    .get(10, TimeUnit.SECONDS)

                val receivedChats = CopyOnWriteArrayList<WaveChatBroadcastPayload>()
                listenerSession.subscribe("/topic/wave/${room.id}/chat", object : StompFrameHandler {
                    override fun getPayloadType(headers: StompHeaders): Type = WaveChatBroadcastPayload::class.java
                    override fun handleFrame(headers: StompHeaders, payload: Any?) {
                        if (payload is WaveChatBroadcastPayload) receivedChats.add(payload)
                    }
                })
                TimeUnit.MILLISECONDS.sleep(500)

                Then("두 번째 메시지는 쿨다운에 막혀 브로드캐스트되지 않는다") {
                    senderSession.send("/app/wave/${room.id}/chat", mapOf("content" to "first"))
                    TimeUnit.MILLISECONDS.sleep(1000)
                    senderSession.send("/app/wave/${room.id}/chat", mapOf("content" to "second"))
                    TimeUnit.SECONDS.sleep(2)

                    receivedChats.size shouldBe 1
                    receivedChats[0].content shouldBe "first"

                    senderSession.disconnect()
                    listenerSession.disconnect()
                }
            }
        }
    }
}

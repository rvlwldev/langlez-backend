package com.langlez.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient
import org.springframework.test.web.reactive.server.WebTestClient
import reactor.core.publisher.Mono
import reactor.netty.DisposableServer
import reactor.netty.http.server.HttpServer
import java.net.URI
import java.util.concurrent.atomic.AtomicReference

/**
 * 가짜 백엔드를 띄우고, 게이트웨이를 거친 요청이 경로·쿼리·헤더·본문·상태 코드·웹소켓까지 그대로 넘어가는지 본다.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    // 테스트가 localhost:8888 의 config-server 를 찾느라 기다리지 않게 끈다
    properties = ["spring.cloud.config.enabled=false"],
)
class GatewayRoutingTest {
    @Value("\${local.server.port}")
    private var port: Int = 0

    private fun client() = WebTestClient.bindToServer().baseUrl("http://localhost:$port").build()

    @Test
    fun `경로와 쿼리와 Authorization 헤더를 그대로 넘긴다`() {
        client().get().uri("/members/me?lang=ko").header("Authorization", "Bearer abc")
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .consumeWith {
                assertThat(it.responseBody).contains("GET /members/me?lang=ko", "auth=Bearer abc")
            }
    }

    @Test
    fun `Host 헤더는 게이트웨이로 들어온 값을 그대로 넘긴다`() {
        client().get().uri("/members/me")
            .exchange()
            .expectBody(String::class.java)
            .consumeWith { assertThat(it.responseBody).contains("host=localhost:$port") }
    }

    @Test
    fun `POST 본문과 백엔드의 상태 코드와 응답 헤더를 그대로 돌려준다`() {
        client().post().uri("/echo").bodyValue("hello")
            .exchange()
            .expectStatus().isCreated
            .expectHeader().valueEquals("X-Backend", "legacy")
            .expectBody(String::class.java).isEqualTo("hello")
    }

    @Test
    fun `백엔드가 404 를 주면 게이트웨이도 404 를 준다`() {
        client().get().uri("/no-such-path").exchange().expectStatus().isNotFound
    }

    @Test
    fun `웹소켓(STOMP 경로)도 그대로 넘긴다`() {
        val received = AtomicReference<String>()
        ReactorNettyWebSocketClient().execute(URI("ws://localhost:$port/ws/chat")) { session ->
            session.send(Mono.just(session.textMessage("ping")))
                .thenMany(session.receive().take(1).map { it.payloadAsText })
                .doOnNext(received::set)
                .then()
        }.block(java.time.Duration.ofSeconds(10))

        assertThat(received.get()).isEqualTo("ping")
    }

    companion object {
        private val backend: DisposableServer = HttpServer.create().port(0).route { routes ->
            routes.get("/members/me") { req, res ->
                res.sendString(
                    Mono.just(
                        "${req.method()} ${req.uri()} auth=${req.requestHeaders()["Authorization"]} host=${req.requestHeaders()["Host"]}",
                    ),
                )
            }
            routes.post("/echo") { req, res ->
                res.status(201).header("X-Backend", "legacy").send(req.receive().retain())
            }
            routes.ws("/ws/chat") { inbound, outbound -> outbound.send(inbound.receive().retain()) }
        }.bindNow()

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("gateway.legacy-uri") { "http://localhost:${backend.port()}" }
        }

        @JvmStatic
        @AfterAll
        fun stopBackend() {
            backend.disposeNow()
        }
    }
}

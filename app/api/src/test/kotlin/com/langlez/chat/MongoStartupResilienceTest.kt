package com.langlez.chat

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.api.response.MemberMeResponse
import com.langlez.utility.JwtTokenProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.net.ServerSocket

/**
 * README §5.1-3 회귀 고정.
 *
 * `auto-index-creation: true` 였을 때는 `MongoTemplate` 빈 생성 시점(컨텍스트 refresh 중)에
 * `ChatMessage` 인덱스를 동기로 쐈다. Mongo 가 응답하지 않으면 서버 선택 타임아웃까지 블로킹한 뒤
 * `chatMessageMongoRepository` → `chatService` → `chatController` 의존 체인이 무너져 컨텍스트
 * refresh 자체가 취소됐다. `chat` 은 `app/api` 가 항상 조립하므로 회피 경로가 없었다.
 *
 * Mongo URI 를 아무도 듣지 않는 포트로 돌려 연결이 즉시 거부되는 상황을 재현한다. 이 상태에서도
 * 조립된 앱(`MainApplication`)이 뜨고, Mongo 와 무관한 회원 조회(`GET /api/v1/members/me`)가
 * 정상 동작하는지 확인한다.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "spring.main.allow-bean-definition-overriding=true",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class MongoStartupResilienceTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var context: ConfigurableApplicationContext
    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var memberRepo: MemberRepository
    @Autowired lateinit var jwt: JwtTokenProvider

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

        // 아무도 듣지 않는 로컬 포트. 연결이 즉시 거부돼 Mongo 가 죽어 있는 상황을 재현한다.
        // 서버 선택 타임아웃도 짧게 잡아 RED(수정 전 코드) 확인이 30초씩 걸리지 않게 한다.
        private val closedMongoPort = ServerSocket(0).use { it.localPort }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.mongodb.uri") {
                "mongodb://localhost:$closedMongoPort/langlez_db?directConnection=true&serverSelectionTimeoutMS=1000"
            }
        }
    }

    init {
        Given("MongoDB 가 응답하지 않는 상태에서 앱을 기동하면") {
            Then("컨텍스트가 뜬다") {
                context.isActive shouldBe true
            }

            val member = memberRepo.save(
                Member(
                    email = "mongoless@test.com",
                    handle = "mongoless",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-mongoless",
                    providerDisplayName = "Mongoless",
                )
            )
            val token = jwt.createAccessToken(member.id, member.handle, "ROLE_MEMBER")

            Then("Mongo 와 무관한 회원 조회는 정상 동작한다") {
                val headers = HttpHeaders().apply { setBearerAuth(token) }
                val response = restTemplate.exchange(
                    "/api/v1/members/me",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    MemberMeResponse::class.java,
                )

                response.statusCode shouldBe HttpStatus.OK
                response.body?.handle shouldBe "mongoless"
            }
        }
    }
}

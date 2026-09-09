package com.langlez.member

import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.security.TokenManager
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * `updateHandle` 의 사전조회(`repo.find(newHandle) != null`)만 가로챈다. HTTP 요청 스레드는
 * 서버(내장 톰캣) 워커 스레드라 테스트 스레드 이름을 못 이어받으므로, 스레드가 아니라
 * **조회한 handle 값**으로 골라낸다 — 이 테스트가 유일하게 [GateState.targetHandle] 을 쓴다.
 *
 * 두 요청이 여기서 반드시 만나야 둘 다 "비어있음"을 보고 유니크 제약까지 넘어간다. 안 그러면
 * 뒤 요청의 사전조회가 앞 요청의 커밋 이후에 돌아 평범하게 409(사전조회 경로)로 걸러지고,
 * PR #51 리뷰가 지적한 "커밋 시점 위반" 경로를 아예 못 태운다.
 */
private object GateState {
    @Volatile var targetHandle: String? = null
    val bothPrechecked: CyclicBarrier = CyclicBarrier(2)
}

private class GatedMemberRepository(private val delegate: MemberRepository) : MemberRepository by delegate {
    override fun find(handle: String): Member? {
        val found = delegate.find(handle)
        if (handle == GateState.targetHandle) {
            GateState.bothPrechecked.await(10, TimeUnit.SECONDS)
        }
        return found
    }
}

@TestConfiguration
class MemberHandleConcurrencyTestConfig {

    @Bean
    @Primary
    fun memberOnlineTracker(): MemberOnlineTracker = mockk(relaxed = true)

    @Bean
    @Primary
    fun gatedMemberRepository(@Qualifier("memberRepositoryImpl") delegate: MemberRepository): MemberRepository =
        GatedMemberRepository(delegate)
}

/**
 * PR #51 리뷰 Important 1 회귀 테스트.
 *
 * `repo.save` 는 `em.merge()` 라 즉시 플러시하지 않는다 — 실제 `UPDATE` 는 `updateHandle` 이
 * 반환된 뒤 `@Transactional` 프록시가 커밋할 때 나간다. 두 트랜잭션이 사전조회를 나란히 통과한
 * 뒤 유니크 제약을 어기면, 그 위반은 서비스 내부 try-catch 밖(커밋 시점)에서 터진다.
 * `MemberIntegrationTest` 의 순차 시나리오(사전조회에서 걸러지는 케이스)는 이 경로를 안 덮는다.
 */
@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
@Import(MemberHandleConcurrencyTestConfig::class)
class MemberHandleConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var rest: TestRestTemplate

    @Autowired
    lateinit var memberService: MemberService

    @Autowired
    lateinit var tokens: TokenManager

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

    private fun headers(token: String) = HttpHeaders().apply {
        contentType = MediaType.APPLICATION_JSON
        setBearerAuth(token)
    }

    init {
        Given("서로 다른 두 회원이 같은 새 핸들로 동시에 변경을 시도하면") {
            val a = memberService.createMember(
                email = "race-a@test.com",
                providerType = Member.Provider.GOOGLE,
                providerId = "race-a",
                providerUsername = "RaceA",
            )
            val b = memberService.createMember(
                email = "race-b@test.com",
                providerType = Member.Provider.GOOGLE,
                providerId = "race-b",
                providerUsername = "RaceB",
            )

            val tokenA = tokens.issueAccessToken(a.id, a.handle, a.role.authority)
            val tokenB = tokens.issueAccessToken(b.id, b.handle, b.role.authority)
            val targetHandle = "racehandle1"
            GateState.targetHandle = targetHandle

            When("사전조회를 둘 다 통과시켜 유니크 제약까지 몰아넣으면") {
                val executor = Executors.newFixedThreadPool(2)
                val calls = listOf(tokenA, tokenB).map { token ->
                    Callable {
                        rest.exchange(
                            "/api/v1/members/me/handle",
                            HttpMethod.PATCH,
                            HttpEntity("""{"handle":"$targetHandle"}""", headers(token)),
                            String::class.java,
                        )
                    }
                }

                val responses = executor.invokeAll(calls, 20, TimeUnit.SECONDS).map { it.get() }
                executor.shutdown()

                Then("한쪽만 200 이고 다른 한쪽은 409 다 (500 이면 안 고쳐진 것이다)") {
                    val statuses = responses.map { it.statusCode }
                    statuses.count { it == HttpStatus.OK } shouldBe 1
                    statuses.count { it == HttpStatus.CONFLICT } shouldBe 1
                }
            }
        }
    }
}

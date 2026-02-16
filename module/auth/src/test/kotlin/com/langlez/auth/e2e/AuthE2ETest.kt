package com.langlez.auth.e2e

import com.langlez.auth.api.TokenResponse
import com.langlez.auth.config.TestSecurityConfig
import com.langlez.member.domain.Member
import com.langlez.member.infrastructure.persistence.jpa.MemberJpaRepository
import com.langlez.security.token.JwtTokenProvider
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Import
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers


@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestSecurityConfig::class)
@DisplayName("E2E: 토큰 갱신 통합 테스트")
class AuthE2ETest : BehaviorSpec() {
    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var repo: MemberJpaRepository

    @Autowired
    lateinit var redis: StringRedisTemplate

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    companion object {
        @Container
        val mysql = MySQLContainer<Nothing>("mysql:8.0").apply {
            withDatabaseName("langlez_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        @Container
        val redis = GenericContainer<Nothing>("redis:7-alpine").apply {
            withExposedPorts(6379)
            start()
        }

        @JvmStatic
        @DynamicPropertySource
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
            registry.add("spring.jpa.hibernate.ddl-auto") { "create" }

            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port", redis::getFirstMappedPort)
        }
    }

    init {
        beforeSpec {
            RestAssured.port = port
            repo.deleteAll()
            redis.keys("*")?.forEach { redis.delete(it) }
        }

        Given("로그인한 회원이 있고 Refresh Token이 발급된 상태에서") {
            val email = "refresh@test.com"
            val member = Member.create(
                nickname = "refresher",
                email = email,
                providerId = "google_refresh",
                providerType = "GOOGLE",
                providerUserName = "Refresh User"
            )
            repo.save(member)

            val initialRefreshToken = tokenProvider.createRefreshToken(email)
            redis.opsForValue().set("refresh_token:$email", initialRefreshToken)

            Thread.sleep(2000) // JWT iat 차이를 위해 넉넉히 대기

            When("Refresh Token으로 토큰 갱신을 요청하면") {
                val refreshRequest = mapOf("refreshToken" to initialRefreshToken)
                val response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .body(refreshRequest)
                    .`when`()
                    .post("/api/v1/auth/refresh")

                Then("새로운 Access Token과 Refresh Token이 발급되어야 한다") {
                    response.statusCode shouldBe 200

                    val tokenResponse = response.`as`(TokenResponse::class.java)
                    tokenResponse.accessToken shouldNotBe null
                    tokenResponse.refreshToken shouldNotBe null
                    tokenResponse.refreshToken shouldNotBe initialRefreshToken // Token Rotation
                }
            }
        }
    }
}

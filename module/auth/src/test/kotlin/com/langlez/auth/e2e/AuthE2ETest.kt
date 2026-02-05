package com.langlez.auth.e2e

import com.langlez.member.domain.Member
import com.langlez.member.domain.repository.MemberRepository
import com.langlez.security.token.JwtTokenProvider
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldNotBe
import io.restassured.RestAssured
import io.restassured.http.ContentType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.TestPropertySource
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

import org.springframework.data.redis.core.StringRedisTemplate
import org.testcontainers.containers.GenericContainer

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@TestPropertySource(properties = ["spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.show-sql=true"])
@Testcontainers
class AuthE2ETest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var redisTemplate: StringRedisTemplate

    companion object {
        @Container
        val mysql = MySQLContainer<Nothing>("mysql:8.0").apply {
            withDatabaseName("langlez_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        @Container
        val redis = GenericContainer<Nothing>("redis:7.0").apply {
            withExposedPorts(6379)
            start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)

            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port", redis::getFirstMappedPort)
        }
    }

    init {
        beforeSpec {
            RestAssured.port = port
        }

        test("Refresh Token으로 새로운 Access Token을 발급받을 수 있다 (E2E)") {
            // Given: 회원 저장 및 유효한 Refresh Token 생성
            val email = "test@example.com"
            val member = Member(
                email = email,
                nickname = "test_user",
                profileImageUrl = "http://test.com/img.png",
                provider = "google",
                providerId = "12345"
            )
            memberRepository.save(member)

            val refreshToken = tokenProvider.createRefreshToken(email)
            redisTemplate.opsForValue().set("refresh_token:$email", refreshToken)

            // When: Refresh API 호출
            val response = RestAssured.given()
                .contentType(ContentType.JSON)
                .body("""{"refreshToken": "$refreshToken"}""")
                .`when`()
                .post("/api/auth/refresh")
                .then()
                .log().all()
                .statusCode(200)
                .extract()
                .`as`(com.langlez.auth.api.TokenResponse::class.java)

            // Then: 새로운 토큰 발급 확인
            response.accessToken shouldNotBe null
            response.refreshToken shouldNotBe null
        }
    }
}


package com.langlez.member.e2e

import com.langlez.file.application.FileStorage
import com.langlez.member.api.request.InitHandleNicknameRequestV1
import com.langlez.member.api.response.ProfileResponse
import com.langlez.member.domain.Member
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import com.langlez.member.MemberTestApplication
import com.langlez.member.infrastructure.persistence.jpa.MemberJpaRepository
import com.langlez.member.infrastructure.persistence.jpa.MemberProfileJpaRepository
import com.langlez.security.token.JwtTokenProvider
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import io.restassured.RestAssured
import io.restassured.http.ContentType
import java.time.LocalDate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService
import org.springframework.security.oauth2.core.user.OAuth2User
import org.springframework.security.web.authentication.AuthenticationSuccessHandler
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    classes = [MemberTestApplication::class],
    properties = ["spring.main.allow-bean-definition-overriding=true"]
)
@Import(MemberE2ETest.TestConfig::class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@DisplayName("E2E: 회원 프로필 관리 및 초기화 통합 테스트")
class MemberE2ETest : BehaviorSpec() {
    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var repo: MemberJpaRepository

    @Autowired
    lateinit var profileRepo: MemberProfileJpaRepository

    @Autowired
    lateinit var tokenProvider: JwtTokenProvider

    private val email = "e2e-user@langlez.com"
    private lateinit var token: String

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testFileStorage(): FileStorage {
            return object : FileStorage {
                override suspend fun upload(file: MultipartFile, folder: String?): String {
                    return "https://cdn.langlez.com/test/${file.originalFilename}"
                }

                override suspend fun delete(fileUrl: String) {
                }
            }
        }

        @Bean
        @Primary
        fun oAuth2UserService(): OAuth2UserService<OAuth2UserRequest, OAuth2User> = mockk(relaxed = true)

        @Bean
        @Primary
        fun authenticationSuccessHandler(): AuthenticationSuccessHandler = mockk(relaxed = true)

        @Bean
        fun clientRegistrationRepository(): ClientRegistrationRepository = mockk(relaxed = true)
    }

    companion object {
        @Container
        val mysql = MySQLContainer<Nothing>("mysql:8.0").apply {
            withDatabaseName("langlez_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        @Container
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7.0")).apply {
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
            
            registry.add("jwt.secret") { "dGhpcy1pcy1hLXZlcnktbG9uZy1zZWNyZXQta2V5LWZvci10ZXN0aW5nLXB1cnBvc2VzLWJhc2U2NA==" }
        }
    }

    init {
        beforeSpec {
            RestAssured.port = port
            profileRepo.deleteAllInBatch()
            repo.deleteAllInBatch()
            
            val member = Member.create(
                nickname = "신규회원",
                email = email,
                providerId = "google_12345",
                providerType = "GOOGLE",
                providerUserName = "Test User"
            )
            repo.saveAndFlush(member)
            
            token = tokenProvider.createAccessToken(email, "ROLE_MEMBER")
        }

        Given("회원 가입 초기 상태에서") {
            
            When("인증 없이 API를 호출하면") {
                val response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .get("/api/v1/members/me")
                
                Then("401 Unauthorized 응답을 받아야 한다") {
                    response.statusCode shouldBe 401
                }
            }

            When("사용자명(Username)을 설정하면") {
                val request = InitHandleNicknameRequestV1("langlez_dev", "개발자")
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(request)
                    .`when`()
                    .put("/api/v1/profiles/me/username")

                Then("성공적으로 업데이트되어야 한다") {
                    response.statusCode shouldBe 200
                    val body = response.`as`(ProfileResponse::class.java)
                    body.username shouldBe "langlez_dev"
                    body.nickname shouldBe "개발자"
                    body.isInitDone shouldBe false
                }
            }

            When("중복된 사용자명으로 변경을 시도하면") {
                val otherMember = Member.create("other", "other@test.com", "id2", "GOOGLE", "u").apply { 
                    username = "duplicate_target"
                }
                repo.saveAndFlush(otherMember)

                val request = InitHandleNicknameRequestV1("duplicate_target", "중복체크")
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(request)
                    .`when`()
                    .put("/api/v1/profiles/me/username")

                Then("409 Conflict 응답을 받아야 한다") {
                    response.statusCode shouldBe 409
                }
            }

            When("잘못된 형식의 사용자명(특수문자 포함)을 입력하면") {
                val request = InitHandleNicknameRequestV1("invalid@name", "닉네임")
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(request)
                    .`when`()
                    .put("/api/v1/profiles/me/username")

                Then("400 Bad Request 응답을 받아야 한다") {
                    response.statusCode shouldBe 400
                }
            }
        }

        Given("프로필 정보 입력 단계에서") {
            
            When("성향 정보를 입력하면") {
                val personality = MemberPersonality(
                    birthDay = LocalDate.of(1995, 5, 5),
                    nationality = MemberPersonality.Nationality.of("KR"),
                    gender = MemberPersonality.Gender.MALE,
                    mbti = MemberPersonality.MBTI.ENTP
                )
                
                RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(personality)
                    .`when`()
                    .put("/api/v1/profiles/me/personality")
                    .then()
                    .statusCode(200)
            }

            When("위치 정보를 입력하면") {
                val location = MemberLocation("Seoul, Korea", 37.5665, 126.9780)
                
                RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(location)
                    .`when`()
                    .put("/api/v1/profiles/me/location")
                    .then()
                    .statusCode(200)
            }

            When("자기소개를 입력하면") {
                val introduction = MemberIntroduction("안녕하세요", "언어 교환 원해요", "친구해요")
                
                RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(introduction)
                    .`when`()
                    .put("/api/v1/profiles/me/introduction")
                    .then()
                    .statusCode(200)
            }

            When("언어 정보를 입력하면") {
                val languages = listOf(
                    MemberLanguage(MemberLanguage.Language.KOREAN, MemberLanguage.Level.NATIVE),
                    MemberLanguage(MemberLanguage.Language.ENGLISH, MemberLanguage.Level.MIDDLE)
                )
                
                RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(languages)
                    .`when`()
                    .put("/api/v1/profiles/me/languages")
                    .then()
                    .statusCode(200)
            }
        }

        Given("마지막으로 프로필 이미지를 업로드하면") {
            When("이미지 파일을 전송하면") {
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.MULTIPART)
                    .multiPart("profileImage", "profile.jpg", "dummy-image-content".toByteArray(), "image/jpeg")
                    .`when`()
                    .put("/api/v1/profiles/me/images")

                Then("업로드가 성공하고 초기화 상태(isInitDone)가 true로 변경되어야 한다") {
                    response.statusCode shouldBe 200
                    val body = response.`as`(ProfileResponse::class.java)
                    body.images shouldNotBe emptyList<String>()
                    body.images[0] shouldBe "https://cdn.langlez.com/test/profile.jpg"
                    body.isInitDone shouldBe true
                }
            }
        }

        Given("모든 설정이 완료된 후") {
            When("내 정보를 조회하면") {
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .`when`()
                    .get("/api/v1/members/me")

                Then("모든 프로필 정보가 포함되어 있어야 한다") {
                    response.statusCode shouldBe 200
                    val body = response.`as`(ProfileResponse::class.java)
                    body.username shouldBe "langlez_dev"
                    body.introduction?.bio shouldBe "안녕하세요"
                    body.location?.address shouldBe "Seoul, Korea"
                    body.languages?.size shouldBe 2
                    body.isInitDone shouldBe true
                }
            }

            When("공개 프로필(@username)을 조회하면") {
                val response = RestAssured.given()
                    .contentType(ContentType.JSON)
                    .`when`()
                    .get("/api/v1/members/@langlez_dev")

                Then("해당 유저의 정보가 조회되어야 한다") {
                    response.statusCode shouldBe 200
                    val body = response.`as`(ProfileResponse::class.java)
                    body.email shouldBe email
                    body.nickname shouldBe "개발자"
                }
            }
        }
    }
}

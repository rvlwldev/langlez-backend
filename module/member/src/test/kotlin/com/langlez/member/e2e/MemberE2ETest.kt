package com.langlez.member.e2e

import com.langlez.file.application.FileStorage
import com.langlez.member.api.request.InitHandleNicknameRequestV1
import com.langlez.member.api.response.MemberResponseV1
import com.langlez.member.domain.Member
import com.langlez.member.domain.embedded.MemberIntroduction
import com.langlez.member.domain.embedded.MemberLanguage
import com.langlez.member.domain.embedded.MemberLocation
import com.langlez.member.domain.embedded.MemberPersonality
import com.langlez.member.infrastructure.persistence.jpa.MemberJpaRepository
import com.langlez.security.token.JwtTokenProvider
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.web.multipart.MultipartFile
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(MemberE2ETest.TestConfig::class)
@DisplayName("E2E: 회원 초기화 플로우 통합 테스트")
class MemberE2ETest : BehaviorSpec() {
    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    lateinit var repo: MemberJpaRepository

    @Autowired
    lateinit var storage: FileStorage

    @Autowired
    @Suppress("SpringJavaInjectionPointsAutowiringInspection")
    lateinit var tokenProvider: JwtTokenProvider

    @TestConfiguration
    class TestConfig {
        @Bean
        @Primary
        fun testFileStorage(): FileStorage {
            return object : FileStorage {
                override fun upload(file: MultipartFile, folder: String?): String {
                    return "https://mock-s3.com/image.jpg"
                }

                override fun delete(fileUrl: String) {
                    // Do nothing
                }
            }
        }
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
        val redis = GenericContainer<Nothing>(DockerImageName.parse("redis:7.0")).apply {
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
        val email = "newuser@test.com"

        beforeSpec {
            RestAssured.port = port
            repo.deleteAll()
        }

        Given("신규 회원이 가입되어 있고 로그인 토큰이 발급된 상태에서") {
            val member =
                Member.create(
                    nickname = "임시닉네임",
                    email = email,
                    providerId = "google_12345",
                    providerType = "GOOGLE",
                    providerUserName = "Test User"
                )
            repo.save(member)

            val token = tokenProvider.createAccessToken(email, "ROLE_MEMBER")

            When("핸들과 닉네임 설정을 요청하면") {
                val handleRequest = InitHandleNicknameRequestV1("langlez_user", "랭글레즈")
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(handleRequest)
                    .`when`()
                    .post("/api/v1/members/init/handle")

                Then("핸들과 닉네임이 업데이트되어야 한다") {
                    response.statusCode shouldBe 200

                    val responseBody = response.`as`(MemberResponseV1::class.java)
                    responseBody.handle shouldBe "langlez_user"
                    responseBody.nickname shouldBe "랭글레즈"
                    responseBody.init shouldBe false
                }
            }

            When("성향 정보(Personality) 설정을 요청하면") {
                val personality = MemberPersonality(
                    birthDay = LocalDate.of(1990, 1, 1),
                    nationality = MemberPersonality.Nationality.of("KR"),
                    gender = MemberPersonality.Gender.MALE,
                    mbti = MemberPersonality.MBTI.INTJ
                )

                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(personality)
                    .`when`()
                    .post("/api/v1/members/init/personality")

                Then("요청이 성공해야 한다") { response.statusCode shouldBe 200 }
            }

            When("위치 정보(Location) 설정을 요청하면") {
                val location = MemberLocation("서울특별시", 37.0, 127.0)
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(location)
                    .`when`()
                    .post("/api/v1/members/init/location")

                Then("요청이 성공해야 한다") { response.statusCode shouldBe 200 }
            }

            When("자기소개(Introduction) 설정을 요청하면") {
                val introduction = MemberIntroduction("안녕하세요", "영어 마스터", "적극적인 파트너")
                val response =
                    RestAssured.given()
                        .header("Authorization", "Bearer $token")
                        .contentType(ContentType.JSON)
                        .body(introduction)
                        .`when`()
                        .post("/api/v1/members/init/introduction")

                Then("요청이 성공해야 한다") { response.statusCode shouldBe 200 }
            }

            When("언어 정보(Languages) 설정을 요청하면") {
                val languages =
                    listOf(
                        MemberLanguage(
                            MemberLanguage.Language.KOREAN,
                            MemberLanguage.Level.NATIVE
                        ),
                        MemberLanguage(
                            MemberLanguage.Language.ENGLISH,
                            MemberLanguage.Level.MIDDLE
                        )
                    )
                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.JSON)
                    .body(languages)
                    .`when`()
                    .post("/api/v1/members/init/languages")

                Then("요청이 성공해야 한다") { response.statusCode shouldBe 200 }
            }

            When("프로필 이미지(Images) 설정을 요청하면") {
                val profileImage = MockMultipartFile(
                    "profileImage",
                    "profile.jpg",
                    "image/jpeg",
                    "profile-data".toByteArray()
                )

                val response = RestAssured.given()
                    .header("Authorization", "Bearer $token")
                    .contentType(ContentType.MULTIPART)
                    .multiPart(
                        profileImage.name,
                        profileImage.originalFilename,
                        profileImage.bytes,
                        profileImage.contentType
                    )
                    .`when`()
                    .post("/api/v1/members/init/images")

                Then("요청이 성공해야 한다") { response.statusCode shouldBe 200 }
            }

            When("초기화 완료(Finish)를 요청하면") {
                val response =
                    RestAssured.given()
                        .header("Authorization", "Bearer $token")
                        .contentType(ContentType.JSON)
                        .`when`()
                        .post("/api/v1/members/init/finish")

                Then("초기화 상태가 완료(true)가 되고 모든 정보가 조회되어야 한다") {
                    response.statusCode shouldBe 200
                    val finalResponse = response.`as`(MemberResponseV1::class.java)
                    finalResponse.init shouldBe true
                    finalResponse.handle shouldBe "langlez_user"
                    finalResponse.nationality shouldBe "KR"
                    finalResponse.address shouldBe "서울특별시"
                }

                When("/me 엔드포인트로 내 정보를 조회하면") {
                    val response =
                        RestAssured.given()
                            .header("Authorization", "Bearer $token")
                            .contentType(ContentType.JSON)
                            .`when`()
                            .get("/api/v1/members/me")

                    Then("저장된 정보와 일치해야 한다") {
                        response.statusCode shouldBe 200
                        val meResponse = response.`as`(MemberResponseV1::class.java)
                        meResponse.email shouldBe email
                        meResponse.handle shouldBe "langlez_user"
                        meResponse.init shouldBe true
                    }
                }

                When("핸들(@langlez_user)로 회원을 조회하면") {
                    val response =
                        RestAssured.given()
                            .contentType(ContentType.JSON)
                            .`when`()
                            .get("/api/v1/members/@langlez_user")

                    Then("해당 회원의 정보가 반환되어야 한다") {
                        response.statusCode shouldBe 200
                        val handleLookupResponse = response.`as`(MemberResponseV1::class.java)
                        handleLookupResponse.handle shouldBe "langlez_user"
                        handleLookupResponse.nickname shouldBe "랭글레즈"
                    }
                }
            }
        }
    }
}

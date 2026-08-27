package com.langlez.profile

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import com.langlez.profile.api.ProfileRequest
import com.langlez.profile.api.ProfileResponse
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileRepository
import com.langlez.profile.infrastructure.jpa.ProfileImageJpaRepository
import com.langlez.profile.infrastructure.jpa.ProfileJpaRepository
import com.langlez.utility.JwtTokenProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedissonAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.main.allow-bean-definition-overriding=true",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
@Import(ProfileE2ETest.TestRedisConfig::class)
class ProfileE2ETest : BehaviorSpec() {

    @TestConfiguration
    class TestRedisConfig {
        @Bean
        fun redissonClient(): RedissonClient {
            val config = Config()
            config.useSingleServer()
                .setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
            return Redisson.create(config)
        }
    }

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var restTemplate: TestRestTemplate
    @Autowired lateinit var memberRepo: MemberRepository
    @Autowired lateinit var profileRepo: ProfileRepository
    @Autowired lateinit var memberJpa: MemberJpaRepository
    @Autowired lateinit var profileJpa: ProfileJpaRepository
    @Autowired lateinit var imageJpa: ProfileImageJpaRepository
    @Autowired lateinit var jwt: JwtTokenProvider
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    // 클래스 레벨 상태 — beforeSpec에서 초기화, 각 When은 이미지만 정리
    private lateinit var alice: Member
    private lateinit var aliceToken: String
    private lateinit var bobToken: String

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

        @JvmField
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:6.0")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") {
                postgres.jdbcUrl
            }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
        }
    }


    /** presign → 실제 PUT 업로드 → 확정에 쓸 key 반환. 확정은 이제 key 로만 받는다. */
    private fun uploadAndGetKey(token: String, filename: String): String {
        val presign = restTemplate.exchange(
            "/api/v1/profiles/images/upload-url?filename=$filename&contentType=image/jpeg",
            HttpMethod.GET,
            HttpEntity<Void>(authHeaders(token)),
            Map::class.java,
        ).body!!
        val key = presign["key"].toString()

        val headers = HttpHeaders()
        headers.putAll(authHeaders(token))
        headers.contentType = MediaType.IMAGE_JPEG
        restTemplate.exchange(
            "/attachments/$key",
            HttpMethod.PUT,
            HttpEntity(byteArrayOf(1, 2, 3), headers),
            String::class.java,
        )
        return key
    }

    init {
        beforeSpec {
            alice = memberRepo.save(
                Member(
                    email = "alice@test.com",
                    handle = "alice",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-alice",
                    providerDisplayName = "Alice"
                )
            )
            val bob = memberRepo.save(
                Member(
                    email = "bob@test.com",
                    handle = "bob",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-bob",
                    providerDisplayName = "Bob"
                )
            )
            transactionTemplate.execute {
                profileJpa.save(Profile(id = alice.id, member = memberJpa.getReferenceById(alice.id)))
            }
            aliceToken = jwt.createAccessToken(alice.id, "alice", "ROLE_MEMBER")
            bobToken = jwt.createAccessToken(bob.id, "bob", "ROLE_MEMBER")
        }

        // 각 테스트 후 이미지만 정리 (멤버/프로필은 공유)
        afterEach {
            imageJpa.deleteAll()
        }

        afterSpec {
            imageJpa.deleteAll()
            profileJpa.deleteAll()
            memberJpa.deleteAll()
        }

        Given("Alice와 Bob이 가입되어 있고 Alice의 프로필이 존재할 때") {

            When("Bob이 Alice의 프로필을 조회하면") {
                val headers = HttpHeaders().apply { setBearerAuth(bobToken) }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/alice",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    ProfileResponse.Detail::class.java,
                )

                Then("200 OK와 Alice의 프로필이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body?.handle shouldBe "alice"
                    response.body?.visitCount shouldNotBe null
                }
            }

            When("존재하지 않는 username으로 프로필을 조회하면") {
                val headers = HttpHeaders().apply { setBearerAuth(bobToken) }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/ghost",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    Any::class.java,
                )

                Then("404 NOT_FOUND가 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }

            When("Alice가 image/jpeg contentType으로 업로드 URL을 요청하면") {
                val headers = HttpHeaders().apply { setBearerAuth(aliceToken) }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/upload-url?filename=photo.jpg&contentType=image/jpeg",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    Map::class.java,
                )

                Then("200 OK와 업로드 URL이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body?.get("presigned").toString() shouldContain "/attachments/"
                    response.body?.get("key").toString() shouldContain "profiles/"
                }
            }

            When("Alice가 video/mp4 contentType으로 업로드 URL을 요청하면") {
                val headers = HttpHeaders().apply { setBearerAuth(aliceToken) }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/upload-url?filename=video.mp4&contentType=video/mp4",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    Any::class.java,
                )

                Then("400 BAD_REQUEST가 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }

            When("Alice가 대표 사진을 등록하면") {
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.POST,
                    HttpEntity(
                        ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "represent.jpg")),
                        authHeaders(aliceToken),
                    ),
                    ProfileResponse.Image::class.java,
                )

                Then("201 Created와 represent=true인 사진이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    response.body?.represent shouldBe true
                    response.body?.url shouldNotBe null
                }
            }

            When("Alice가 대표 사진 등록 후 추가 사진을 등록하면") {
                restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.POST,
                    HttpEntity(
                        ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "first.jpg")),
                        authHeaders(aliceToken),
                    ),
                    ProfileResponse.Image::class.java,
                )
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images",
                    HttpMethod.POST,
                    HttpEntity(
                        ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "second.jpg")),
                        authHeaders(aliceToken),
                    ),
                    ProfileResponse.Image::class.java,
                )

                Then("201 Created와 represent=false인 추가 사진이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    response.body?.represent shouldBe false
                    response.body?.url shouldNotBe null
                }
            }

            When("Alice가 기존 추가 사진으로 대표 사진을 변경하면") {
                restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "old.jpg")), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )
                val added = restTemplate.exchange(
                    "/api/v1/profiles/images",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "new.jpg")), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.PATCH,
                    HttpEntity(ProfileRequest.ImageSelect(added.body!!.url), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )

                Then("200 OK와 변경된 대표 사진이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body?.represent shouldBe true
                    response.body?.url shouldNotBe null
                }
            }

            When("Alice가 등록되지 않은 URL로 대표 사진 변경을 요청하면") {
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.PATCH,
                    HttpEntity(ProfileRequest.ImageSelect("https://cdn/profiles/ghost.jpg"), authHeaders(aliceToken)),
                    Any::class.java,
                )

                Then("404 NOT_FOUND가 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }

            When("Alice가 사진 6장 등록 후 추가로 등록하면") {
                restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "1.jpg")), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )
                for (i in 2..6) {
                    restTemplate.exchange(
                        "/api/v1/profiles/images",
                        HttpMethod.POST,
                        HttpEntity(ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "$i.jpg")), authHeaders(aliceToken)),
                        ProfileResponse.Image::class.java,
                    )
                }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm(uploadAndGetKey(aliceToken, "7.jpg")), authHeaders(aliceToken)),
                    Any::class.java,
                )

                Then("400 BAD_REQUEST가 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        /**
         * 정지·탈퇴 회원 차단은 JwtAuthenticationFilter 에서 일어난다. 필터에서 던진 예외는
         * @RestControllerAdvice 를 못 타는 게 보통이라, 실제 응답 본문이 JSON 으로 나가는지
         * 조립된 앱에서 확인한다. (필터가 handlerExceptionResolver 로 넘겨 이 경로가 살아 있다.)
         */
        Given("정지·탈퇴된 회원이 유효한 액세스 토큰을 들고 있을 때") {

            // 메시지 문구는 로케일에 따라 달라진다. 실행 환경의 기본 로케일에 기대지 않고 영어로 못 박는다.
            fun englishHeaders(token: String) = authHeaders(token).apply { set(HttpHeaders.ACCEPT_LANGUAGE, "en") }

            fun tokenOf(handle: String, status: Member.Status): String {
                val member = memberRepo.save(
                    Member(
                        email = "$handle@test.com",
                        handle = handle,
                        status = status,
                        provider = Member.Provider.GOOGLE,
                        providerId = "g-$handle",
                        providerDisplayName = handle,
                    )
                )
                return jwt.createAccessToken(member.id, handle, "ROLE_MEMBER")
            }

            When("정지된 회원이 일반 API 를 호출하면") {
                val response = restTemplate.exchange(
                    "/api/v1/profiles/alice",
                    HttpMethod.GET,
                    HttpEntity<Void>(englishHeaders(tokenOf("carol", Member.Status.SUSPENDED))),
                    String::class.java,
                )

                Then("403 과 member.suspended 문구가 담긴 JSON 본문이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                    response.body shouldContain "\"status\":\"FORBIDDEN\""
                    response.body shouldContain "This account is suspended."
                }
            }

            When("탈퇴한 회원이 일반 API 를 호출하면") {
                val response = restTemplate.exchange(
                    "/api/v1/profiles/alice",
                    HttpMethod.GET,
                    HttpEntity<Void>(englishHeaders(tokenOf("dave", Member.Status.WITHDRAWN))),
                    String::class.java,
                )

                Then("403 과 탈퇴 문구가 담긴 JSON 본문이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                    response.body shouldContain "\"status\":\"FORBIDDEN\""
                    response.body shouldContain "This account has been deleted."
                }
            }

            // 여기서 403 이 나오면 미인증 판정 경로가 깨진 것이다.
            When("토큰 없이 호출하면") {
                val response = restTemplate.exchange(
                    "/api/v1/profiles/alice",
                    HttpMethod.GET,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("여전히 401 이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }

    private fun authHeaders(token: String) = HttpHeaders().apply {
        setBearerAuth(token)
        set(HttpHeaders.CONTENT_TYPE, "application/json")
    }
}

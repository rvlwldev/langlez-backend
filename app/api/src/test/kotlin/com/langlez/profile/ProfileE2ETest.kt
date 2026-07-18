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
import com.langlez.security.util.JwtParser
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
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer

@SpringBootTest(
    webEnvironment = RANDOM_PORT,
    properties = [
        "spring.autoconfigure.exclude=" +
            "org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedissonAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration," +
            "org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
        "spring.main.allow-bean-definition-overriding=true",
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
    @Autowired lateinit var jwtParser: JwtParser
    @Autowired lateinit var transactionTemplate: TransactionTemplate

    // 클래스 레벨 상태 — beforeSpec에서 초기화, 각 When은 이미지만 정리
    private lateinit var alice: Member
    private lateinit var aliceToken: String
    private lateinit var bobToken: String

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
            registry.add("spring.datasource.url") {
                mysql.jdbcUrl + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8"
            }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
        }
    }

    init {
        beforeSpec {
            alice = memberRepo.save(
                Member(
                    email = "alice@test.com",
                    username = "alice",
                    nickname = "Alice",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-alice",
                    providerDisplayName = "Alice"
                )
            )
            val bob = memberRepo.save(
                Member(
                    email = "bob@test.com",
                    username = "bob",
                    nickname = "Bob",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-bob",
                    providerDisplayName = "Bob"
                )
            )
            transactionTemplate.execute {
                profileJpa.save(Profile(id = alice.id, member = memberJpa.getReferenceById(alice.id)))
            }
            aliceToken = jwtParser.createAccessToken(alice.id, "MEMBER")
            bobToken = jwtParser.createAccessToken(bob.id, "MEMBER")
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
                    "/api/v1/profiles/@alice",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers),
                    ProfileResponse.Detail::class.java,
                )

                Then("200 OK와 Alice의 프로필이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body?.username shouldBe "alice"
                    response.body?.nickname shouldBe "Alice"
                    response.body?.visitCount shouldNotBe null
                }
            }

            When("존재하지 않는 username으로 프로필을 조회하면") {
                val headers = HttpHeaders().apply { setBearerAuth(bobToken) }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/@ghost",
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
                    response.body?.get("uploadUrl").toString() shouldContain "upload"
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
                        ProfileRequest.ImageConfirm("https://cdn/profiles/represent.jpg"),
                        authHeaders(aliceToken),
                    ),
                    ProfileResponse.Image::class.java,
                )

                Then("201 Created와 represent=true인 사진이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    response.body?.represent shouldBe true
                    response.body?.url shouldBe "https://cdn/profiles/represent.jpg"
                }
            }

            When("Alice가 대표 사진 등록 후 추가 사진을 등록하면") {
                restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.POST,
                    HttpEntity(
                        ProfileRequest.ImageConfirm("https://cdn/profiles/first.jpg"),
                        authHeaders(aliceToken),
                    ),
                    ProfileResponse.Image::class.java,
                )
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images",
                    HttpMethod.POST,
                    HttpEntity(
                        ProfileRequest.ImageConfirm("https://cdn/profiles/second.jpg"),
                        authHeaders(aliceToken),
                    ),
                    ProfileResponse.Image::class.java,
                )

                Then("201 Created와 represent=false인 추가 사진이 반환된다") {
                    response.statusCode shouldBe HttpStatus.CREATED
                    response.body?.represent shouldBe false
                    response.body?.url shouldBe "https://cdn/profiles/second.jpg"
                }
            }

            When("Alice가 기존 추가 사진으로 대표 사진을 변경하면") {
                restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/old.jpg"), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )
                restTemplate.exchange(
                    "/api/v1/profiles/images",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/new.jpg"), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.PATCH,
                    HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/new.jpg"), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )

                Then("200 OK와 변경된 대표 사진이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body?.represent shouldBe true
                    response.body?.url shouldBe "https://cdn/profiles/new.jpg"
                }
            }

            When("Alice가 등록되지 않은 URL로 대표 사진 변경을 요청하면") {
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images/represent",
                    HttpMethod.PATCH,
                    HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/ghost.jpg"), authHeaders(aliceToken)),
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
                    HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/1.jpg"), authHeaders(aliceToken)),
                    ProfileResponse.Image::class.java,
                )
                for (i in 2..6) {
                    restTemplate.exchange(
                        "/api/v1/profiles/images",
                        HttpMethod.POST,
                        HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/$i.jpg"), authHeaders(aliceToken)),
                        ProfileResponse.Image::class.java,
                    )
                }
                val response = restTemplate.exchange(
                    "/api/v1/profiles/images",
                    HttpMethod.POST,
                    HttpEntity(ProfileRequest.ImageConfirm("https://cdn/profiles/7.jpg"), authHeaders(aliceToken)),
                    Any::class.java,
                )

                Then("400 BAD_REQUEST가 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }
    }

    private fun authHeaders(token: String) = HttpHeaders().apply {
        setBearerAuth(token)
        set(HttpHeaders.CONTENT_TYPE, "application/json")
    }
}

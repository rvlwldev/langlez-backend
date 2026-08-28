package com.langlez.profile

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.event.member.MemberCreatedEvent
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import com.langlez.profile.api.ProfileConsumer
import com.langlez.profile.domain.ProfileRepository
import com.langlez.profile.infrastructure.jpa.ProfileJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer

/**
 * 프로필 행을 만드는 코드가 저장소 전체에 없어서 프로필 API 가 전부 404 였다.
 * 이제 `member-created` 컨슈머가 만든다. 카프카는 at-least-once 라 같은 이벤트가 다시 오는데,
 * 그때 PK 충돌로 죽으면 파티션이 통째로 막힌다.
 *
 * member_profiles.id 가 members(id) 로의 FK 라 회원 행이 먼저 있어야 한다.
 * 두 모듈을 가로지르므로 app/api 에 둔다.
 */
@SpringBootTest(
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
@Import(ProfileCreationIntegrationTest.TestRedisConfig::class)
class ProfileCreationIntegrationTest : BehaviorSpec() {

    @TestConfiguration
    class TestRedisConfig {
        @Bean
        fun redissonClient(): RedissonClient = Redisson.create(
            Config().apply {
                useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
            }
        )
    }

    override fun extensions() = listOf(SpringExtension)

    @Autowired lateinit var consumer: ProfileConsumer
    @Autowired lateinit var memberRepo: MemberRepository
    @Autowired lateinit var memberJpa: MemberJpaRepository
    @Autowired lateinit var profileRepo: ProfileRepository
    @Autowired lateinit var profileJpa: ProfileJpaRepository
    @Autowired lateinit var mapper: ObjectMapper

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
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:6.0").also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
        }
    }

    init {
        afterSpec {
            profileJpa.deleteAll()
            memberJpa.deleteAll()
        }

        Given("가입한 회원의 member-created 이벤트가 오면") {
            val member = memberRepo.save(
                Member(
                    email = "created@test.com",
                    handle = "createduser",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-created",
                )
            )
            val payload = mapper.writeValueAsString(
                MemberCreatedEvent(id = member.id, email = member.email, handle = member.handle)
            )

            When("이벤트를 한 번 처리하면") {
                consumer.onMemberCreated(payload)

                Then("회원 id 를 그대로 쓰는 프로필 행이 생긴다") {
                    val profile = profileRepo.findProfile(member.id)
                    profile shouldNotBe null
                    profile!!.id shouldBe member.id
                    profile.visitCount shouldBe 0L
                }
            }

            When("같은 이벤트가 재배달돼 한 번 더 들어오면") {
                Then("예외 없이 넘어가고 프로필은 하나뿐이다") {
                    consumer.onMemberCreated(payload)
                    consumer.onMemberCreated(payload)

                    profileJpa.findAllById(listOf(member.id)).size shouldBe 1
                }
            }
        }

        Given("백필 마이그레이션이 이미 프로필을 만들어 둔 회원이면") {
            val member = memberRepo.save(
                Member(
                    email = "backfilled@test.com",
                    handle = "backfilled",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-backfilled",
                )
            )

            When("뒤늦게 member-created 이벤트가 들어오면") {
                consumer.onMemberCreated(
                    mapper.writeValueAsString(
                        MemberCreatedEvent(id = member.id, email = member.email, handle = member.handle)
                    )
                )
                // 여기까지가 첫 생성. 이제 이미 있는 상태에서 한 번 더 들어온다.

                Then("기존 프로필을 덮어쓰지 않는다") {
                    val before = profileRepo.findProfile(member.id)!!.apply { bio = "직접 쓴 자기소개" }
                    profileRepo.saveProfile(before)

                    consumer.onMemberCreated(
                        mapper.writeValueAsString(
                            MemberCreatedEvent(id = member.id, email = member.email, handle = member.handle)
                        )
                    )

                    profileRepo.findProfile(member.id)!!.bio shouldBe "직접 쓴 자기소개"
                }
            }
        }
    }
}

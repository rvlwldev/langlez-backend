package com.langlez.follow.infrastructure

import com.langlez.follow.contract.FollowReader
import com.langlez.follow.domain.Follow
import com.langlez.follow.domain.FollowRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class FollowRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: FollowRepository

    @Autowired
    lateinit var follows: FollowReader

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

    init {
        Given("한 사람을 여러 명이 팔로우하면") {
            val me = 5001L
            val followers = (5002L..5004L).map { repo.save(Follow(it, me)) }

            Then("최신순(팔로우 행 id 내림차순)으로 나온다") {
                repo.findFollowers(me, 10, null).map { it.memberId } shouldBe listOf(5004L, 5003L, 5002L)
            }

            Then("커서로 자르면 그 뒤부터 이어진다") {
                val first = repo.findFollowers(me, 2, null)
                first.map { it.memberId } shouldBe listOf(5004L, 5003L)

                repo.findFollowers(me, 2, first.last().id).map { it.memberId } shouldBe listOf(5002L)
            }

            Then("커서 값은 created_at 이 아니라 팔로우 행 id 다") {
                repo.findFollowers(me, 10, null).map { it.id } shouldBe followers.map { it.id }.reversed()
            }
        }

        Given("내가 여러 명을 팔로우하면") {
            val me = 5011L
            repo.save(Follow(me, 5012L))
            repo.save(Follow(me, 5013L))

            Then("팔로잉 목록에 상대 id 가 나온다") {
                repo.findFollowings(me, 10, null).map { it.memberId } shouldBe listOf(5013L, 5012L)
            }

            Then("언팔로우하면 한 방향만 사라진다") {
                repo.save(Follow(5012L, me))
                repo.delete(me, 5012L)

                repo.findFollowings(me, 10, null).map { it.memberId } shouldBe listOf(5013L)
                repo.findFollowers(me, 10, null).map { it.memberId } shouldBe listOf(5012L)
            }
        }

        Given("팔로우가 하나도 없는 회원은") {
            val loner = 5041L

            Then("팔로워 수도 팔로잉 수도 0 이다") {
                repo.countFollowers(loner) shouldBe 0L
                repo.countFollowings(loner) shouldBe 0L
            }
        }

        Given("팔로워와 팔로잉이 섞여 있으면") {
            val me = 5051L
            (5052L..5054L).forEach { repo.save(Follow(it, me)) }
            (5055L..5056L).forEach { repo.save(Follow(me, it)) }

            Then("두 방향이 각각 따로 세어진다") {
                repo.countFollowers(me) shouldBe 3L
                repo.countFollowings(me) shouldBe 2L
            }

            // 자기 팔로우 행은 엔티티가 막아 애초에 저장되지 않는다. 카운트가 자기 자신을 포함할 길이 없다.
            Then("자기 자신은 어느 쪽에도 포함되지 않는다") {
                shouldThrow<IllegalArgumentException> { Follow(me, me) }

                repo.findFollowers(me, 50, null).map { it.memberId } shouldNotContain me
                repo.findFollowings(me, 50, null).map { it.memberId } shouldNotContain me
            }

            Then("FollowReader 포트도 같은 숫자를 한 번에 돌려준다") {
                follows.counts(me) shouldBe FollowReader.CountInfo(followers = 3L, followings = 2L)
            }

            Then("언팔로우하면 카운트가 바로 줄어든다") {
                repo.delete(me, 5055L)

                repo.countFollowings(me) shouldBe 1L
            }
        }
    }
}

package com.langlez.relationship.infrastructure

import com.langlez.core.BlockQuery
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.Report
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
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
class RelationshipRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: RelationshipRepository

    @Autowired
    lateinit var blocks: BlockQuery

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
                repo.deleteFollow(me, 5012L)

                repo.findFollowings(me, 10, null).map { it.memberId } shouldBe listOf(5013L)
                repo.findFollowers(me, 10, null).map { it.memberId } shouldBe listOf(5012L)
            }
        }

        Given("차단하면") {
            val me = 5021L
            val other = 5022L
            repo.save(Block(me, other))

            Then("차단 목록에 나오고 양방향 차단 조회가 걸린다") {
                repo.findBlocks(me, 10, null).map { it.memberId } shouldBe listOf(other)
                blocks.isBlockedBetween(other, me) shouldBe true
            }

            Then("해제하면 사라진다") {
                repo.deleteBlock(me, other)

                repo.findBlocks(me, 10, null) shouldHaveSize 0
                blocks.isBlockedBetween(other, me) shouldBe false
            }
        }

        Given("신고를 저장하면") {
            val reporter = 5031L
            repo.save(Report(reporter, 5032L, Report.SourceType.CHAT_USER, "77", "욕설", "m1"))

            Then("같은 (신고자, 출처, 트리거 메시지) 조합은 이미 있는 것으로 판정된다") {
                repo.existsReport(reporter, Report.SourceType.CHAT_USER, "77", "m1") shouldBe true
            }

            Then("트리거 메시지가 다르면 다른 신고다") {
                repo.existsReport(reporter, Report.SourceType.CHAT_USER, "77", "m2") shouldBe false
            }

            Then("트리거 메시지가 없는 신고도 null 로 구분된다") {
                repo.existsReport(reporter, Report.SourceType.CHAT_USER, "77", null) shouldBe false

                repo.save(Report(reporter, 5032L, Report.SourceType.CHAT_USER, "77", "욕설"))
                repo.existsReport(reporter, Report.SourceType.CHAT_USER, "77", null) shouldBe true
            }
        }
    }
}

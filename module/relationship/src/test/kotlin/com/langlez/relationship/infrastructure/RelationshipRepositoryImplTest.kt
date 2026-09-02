package com.langlez.relationship.infrastructure

import com.langlez.relationship.contract.BlockReader
import com.langlez.relationship.contract.FollowReader
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.Report
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldHaveSize
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
class RelationshipRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: RelationshipRepository

    @Autowired
    lateinit var blocks: BlockReader

    @Autowired
    lateinit var follows: FollowReader

    @Autowired
    lateinit var service: RelationshipService

    @Autowired
    lateinit var memberRepo: MemberRepository

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

    private var sequence = 0L

    private fun newMember(prefix: String): Member {
        sequence++
        return Member(
            email = "$prefix$sequence@count.test",
            handle = "$prefix$sequence",
            provider = Member.Provider.GOOGLE,
            providerId = "$prefix$sequence",
        )
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

        /**
         * 목록 화면은 항목마다 묻지 않고 이 배치 판정을 쓴다.
         * 판정 규칙이 `isBlockedBetween` 과 갈라지면 한쪽 경로에서만 차단이 먹는 구멍이 생긴다.
         */
        Given("내가 건 차단과 나에게 걸린 차단이 섞여 있으면") {
            val viewer = 5061L
            val iBlocked = 5062L
            val blockedMe = 5063L
            val unrelated = 5064L

            repo.save(Block(viewer, iBlocked))
            repo.save(Block(blockedMe, viewer))

            Then("양방향 모두 한 번에 걸러진다") {
                blocks.blockedAmong(viewer, listOf(iBlocked, blockedMe, unrelated)) shouldBe
                    setOf(iBlocked, blockedMe)
            }

            Then("단건 판정과 결과가 같다") {
                listOf(iBlocked, blockedMe, unrelated).filter { blocks.isBlockedBetween(viewer, it) } shouldBe
                    listOf(iBlocked, blockedMe)
            }

            Then("자기 자신은 후보에 넣어도 차단으로 잡히지 않는다") {
                blocks.blockedAmong(viewer, listOf(viewer, unrelated)) shouldBe emptySet()
            }

            Then("후보가 비면 조회하지 않고 빈 집합이다") {
                blocks.blockedAmong(viewer, emptyList()) shouldBe emptySet()
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
                repo.deleteFollow(me, 5055L)

                repo.countFollowings(me) shouldBe 1L
            }
        }

        Given("서로 팔로우하던 두 회원 중 하나가 차단하면") {
            val blocker = memberRepo.save(newMember("blocker"))
            val blocked = memberRepo.save(newMember("blocked"))

            repo.save(Follow(blocker.id, blocked.id))
            repo.save(Follow(blocked.id, blocker.id))

            // 차단은 팔로우를 양방향으로 끊는데 그걸 알리는 이벤트가 없다.
            // 비정규화 카운터였다면 여기서 조용히 어긋난다. COUNT 라 그냥 맞는다.
            Then("양쪽 카운트가 0 으로 떨어진다") {
                repo.countFollowers(blocker.id) shouldBe 1L
                repo.countFollowings(blocker.id) shouldBe 1L

                service.block(blocker.id, blocked.id)

                repo.countFollowers(blocker.id) shouldBe 0L
                repo.countFollowings(blocker.id) shouldBe 0L
                repo.countFollowers(blocked.id) shouldBe 0L
                repo.countFollowings(blocked.id) shouldBe 0L
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

package com.langlez.block.infrastructure

import com.langlez.block.contract.BlockReader
import com.langlez.block.domain.Block
import com.langlez.block.domain.BlockRepository
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
class BlockRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: BlockRepository

    @Autowired
    lateinit var blocks: BlockReader

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
        Given("차단하면") {
            val me = 5021L
            val other = 5022L
            repo.save(Block(me, other))

            Then("차단 목록에 나오고 양방향 차단 조회가 걸린다") {
                repo.findBlocks(me, 10, null).map { it.memberId } shouldBe listOf(other)
                blocks.isBlockedBetween(other, me) shouldBe true
            }

            Then("해제하면 사라진다") {
                repo.delete(me, other)

                repo.findBlocks(me, 10, null) shouldHaveSize 0
                blocks.isBlockedBetween(other, me) shouldBe false
            }
        }

        Given("여러 명을 차단하면") {
            val me = 5031L
            val targets = (5032L..5034L).map { repo.save(Block(me, it)) }

            Then("최신순(차단 행 id 내림차순)으로 나온다") {
                repo.findBlocks(me, 10, null).map { it.memberId } shouldBe listOf(5034L, 5033L, 5032L)
            }

            Then("커서로 자르면 그 뒤부터 이어진다") {
                val first = repo.findBlocks(me, 2, null)
                first.map { it.memberId } shouldBe listOf(5034L, 5033L)

                repo.findBlocks(me, 2, first.last().id).map { it.memberId } shouldBe listOf(5032L)
            }

            Then("커서 값은 created_at 이 아니라 차단 행 id 다") {
                repo.findBlocks(me, 10, null).map { it.id } shouldBe targets.map { it.id }.reversed()
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
    }
}

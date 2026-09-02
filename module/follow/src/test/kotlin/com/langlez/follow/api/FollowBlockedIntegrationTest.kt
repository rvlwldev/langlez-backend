package com.langlez.follow.api

import com.langlez.follow.domain.Follow
import com.langlez.follow.domain.FollowRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 차단 → 팔로우 양방향 해제의 실제 경로.
 *
 * 예전에는 `RelationshipService.block()` 이 한 트랜잭션 안에서 차단 저장과 팔로우 삭제를 같이 했다.
 * 모듈이 갈리면서 그게 불가능해졌고 지금은 `member-blocked` 카프카 이벤트로 간다.
 * **여기가 그 경로의 유일한 통합 검증이다** — 이 컨슈머가 죽으면 차단해 놓고 상대 팔로잉 목록에 남는다.
 *
 * 브로커는 띄우지 않는다. 확인할 것은 브로커 왕복이 아니라 "페이로드를 받아 실제로 두 방향을
 * 지우는가" 라서 컨슈머 메서드를 직접 부른다. 발행 쪽(아웃박스 기록)은 block 모듈이 본다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 브로커가 없는 테스트다. 리스너를 띄우면 접속 재시도 로그만 쌓인다.
        "spring.kafka.listener.auto-startup=false",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class FollowBlockedIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var consumer: FollowConsumer

    @Autowired
    lateinit var repo: FollowRepository

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

    private fun payload(blockerId: Long, blockedId: Long, occurredAt: Long) =
        """{"blockerId":$blockerId,"blockedId":$blockedId,"occurredAt":$occurredAt}"""

    init {
        Given("서로 팔로우하던 두 회원 중 하나가 차단하면") {
            val blocker = 7001L
            val blocked = 7002L

            repo.save(Follow(blocker, blocked))
            repo.save(Follow(blocked, blocker))

            When("member-blocked 이벤트가 배달되면") {
                repo.countFollowers(blocker) shouldBe 1L
                repo.countFollowings(blocker) shouldBe 1L

                consumer.onMemberBlocked(payload(blocker, blocked, 1_700_000_000_000L))

                // 한쪽만 끊으면 차단해 놓고 상대 팔로잉 목록에 그대로 남는다.
                Then("양쪽 카운트가 0 으로 떨어진다") {
                    repo.countFollowers(blocker) shouldBe 0L
                    repo.countFollowings(blocker) shouldBe 0L
                    repo.countFollowers(blocked) shouldBe 0L
                    repo.countFollowings(blocked) shouldBe 0L
                }

                Then("팔로우 행 자체가 두 방향 다 사라진다") {
                    repo.find(blocker, blocked) shouldBe null
                    repo.find(blocked, blocker) shouldBe null
                }
            }
        }

        Given("이미 팔로우가 정리된 뒤에 같은 차단 이벤트가 또 오면") {
            val blocker = 7011L
            val blocked = 7012L

            repo.save(Follow(blocker, blocked))

            When("occurredAt 이 다른 이벤트로 두 번 배달되면") {
                consumer.onMemberBlocked(payload(blocker, blocked, 1_700_000_000_001L))
                consumer.onMemberBlocked(payload(blocker, blocked, 1_700_000_000_002L))

                // 없는 관계를 지워도 성공이라 재배달·수습 재발행이 예외로 번지지 않는다.
                Then("예외 없이 끝나고 결과는 같다") {
                    repo.find(blocker, blocked) shouldBe null
                    repo.countFollowings(blocker) shouldBe 0L
                }
            }
        }

        Given("팔로우 관계가 애초에 없던 두 회원이면") {
            val blocker = 7021L
            val blocked = 7022L

            When("차단 이벤트가 배달되면") {
                consumer.onMemberBlocked(payload(blocker, blocked, 1_700_000_000_003L))

                Then("아무 일도 없이 성공한다 (멱등)") {
                    repo.countFollowings(blocker) shouldBe 0L
                }
            }
        }
    }
}

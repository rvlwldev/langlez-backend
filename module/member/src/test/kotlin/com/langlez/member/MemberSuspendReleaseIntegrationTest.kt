package com.langlez.member

import com.langlez.member.application.MemberSuspendReleaseScheduler
import com.langlez.member.application.MemberSuspender
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberSuspendHistory
import com.langlez.member.domain.MemberSuspendHistoryRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS

/**
 * 기간 정지 만료 해제 배치.
 *
 * `releaseAt` 을 기록만 하고 읽는 코드가 없어 "7일 정지"가 영구 정지였다. 그 회귀를 여기서 막는다.
 * 배치가 실제 쿼리(`isReleased = false and release_at <= now`)를 타는지가 핵심이라
 * 목이 아니라 Testcontainers 로 본다.
 *
 * 행을 남기는 스펙이라 [MemberIntegrationTest] 와 파일을 나눈다.
 */
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
class MemberSuspendReleaseIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: MemberRepository

    @Autowired
    lateinit var suspendRepo: MemberSuspendHistoryRepository

    @Autowired
    lateinit var suspender: MemberSuspender

    // 스케줄러가 internal 이라 프로퍼티도 internal 이어야 한다.
    @Autowired
    internal lateinit var scheduler: MemberSuspendReleaseScheduler

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

    private fun newMember(handle: String, status: Member.Status = Member.Status.ACTIVE): Member = repo.save(
        Member(
            email = "$handle@test.com",
            handle = handle,
            status = status,
            provider = Member.Provider.GOOGLE,
            providerId = "p-$handle",
        )
    )

    /** 만료 시각을 직접 넣는다. `suspend(days = ...)` 로는 과거 시각을 만들 수 없다. */
    private fun openHistory(memberId: Long, releaseAt: Instant?) = suspendRepo.save(
        MemberSuspendHistory(memberId = memberId, reason = "test", releaseAt = releaseAt, actorId = 9_999L)
    )

    private fun statusOf(id: Long) = repo.find(id)!!.status

    init {
        Given("기간 정지가 만료된 회원이 있으면") {
            val member = newMember("expired", Member.Status.SUSPENDED)
            openHistory(member.id, Instant.now().minus(1, DAYS))

            scheduler.releaseExpiredBefore(Instant.now())

            Then("정지가 풀려 ACTIVE 가 된다") {
                statusOf(member.id) shouldBe Member.Status.ACTIVE
            }

            Then("이력이 닫힌다") {
                suspendRepo.findOpen(member.id).shouldBeEmpty()
            }

            // 닫지 않으면 매 주기 같은 행을 다시 잡아 배치가 영원히 같은 일을 한다.
            Then("다음 주기에는 다시 잡히지 않는다") {
                suspendRepo.findExpired(Instant.now(), 100)
                    .filter { it.memberId == member.id }
                    .shouldBeEmpty()
            }
        }

        Given("아직 만료되지 않은 정지면") {
            val member = newMember("not-yet", Member.Status.SUSPENDED)
            openHistory(member.id, Instant.now().plus(7, DAYS))

            scheduler.releaseExpiredBefore(Instant.now())

            Then("정지가 유지된다") {
                statusOf(member.id) shouldBe Member.Status.SUSPENDED
                suspendRepo.findOpen(member.id) shouldHaveSize 1
            }
        }

        Given("기간이 없는 무기한 정지면") {
            val member = newMember("forever", Member.Status.SUSPENDED)
            openHistory(member.id, releaseAt = null)

            scheduler.releaseExpiredBefore(Instant.now())

            Then("배치가 건드리지 않는다 (사람이 풀어야 한다)") {
                statusOf(member.id) shouldBe Member.Status.SUSPENDED
                suspendRepo.findOpen(member.id) shouldHaveSize 1
            }
        }

        Given("어드민이 만료 전에 정지를 먼저 푼 회원이면") {
            val member = newMember("manual", Member.Status.ACTIVE)
            suspender.suspend(member.id, reason = "test", days = 7L, actorId = 9_999L)
            suspender.unsuspend(member.id, actorId = 9_999L)

            Then("해제 시점에 이력이 이미 닫혀 있다") {
                suspendRepo.findOpen(member.id).shouldBeEmpty()
            }

            Then("만료 시각이 지나도 배치가 다시 잡지 않는다") {
                scheduler.releaseExpiredBefore(Instant.now().plus(30, DAYS))

                statusOf(member.id) shouldBe Member.Status.ACTIVE
            }
        }

        /**
         * `Member.unsuspend()` 는 `require(status != WITHDRAWN)` 로 던진다.
         * 배치가 그걸 잡지 못하면 그 회원 뒤의 대상이 전부 밀린다.
         */
        Given("만료 대상 중에 탈퇴한 회원이 섞여 있으면") {
            val withdrawn = newMember("gone", Member.Status.WITHDRAWN)
            val normal = newMember("normal", Member.Status.SUSPENDED)
            openHistory(withdrawn.id, Instant.now().minus(1, DAYS))
            openHistory(normal.id, Instant.now().minus(1, DAYS))

            scheduler.releaseExpiredBefore(Instant.now())

            Then("탈퇴 회원의 상태는 그대로다") {
                statusOf(withdrawn.id) shouldBe Member.Status.WITHDRAWN
            }

            Then("탈퇴 회원의 이력도 닫는다 (안 닫으면 매 주기 다시 잡힌다)") {
                suspendRepo.findOpen(withdrawn.id).shouldBeEmpty()
            }

            Then("뒤에 있던 정상 회원은 계속 처리된다") {
                statusOf(normal.id) shouldBe Member.Status.ACTIVE
                suspendRepo.findOpen(normal.id).shouldBeEmpty()
            }
        }

        /**
         * 3일 정지 도중 중대 위반이 적발돼 30일 정지가 덧붙는 경우.
         * 만료된 이력만 보고 회원을 풀면 남은 27일을 통째로 잃는다.
         */
        Given("정지 이력이 둘이고 짧은 쪽만 만료됐으면") {
            val member = newMember("overlap", Member.Status.SUSPENDED)
            openHistory(member.id, Instant.now().minus(1, DAYS))
            val longer = openHistory(member.id, Instant.now().plus(27, DAYS))

            scheduler.releaseExpiredBefore(Instant.now())

            Then("정지가 유지된다") {
                statusOf(member.id) shouldBe Member.Status.SUSPENDED
            }

            Then("만료된 이력은 닫고 남은 이력은 그대로 둔다") {
                suspendRepo.findOpen(member.id).map { it.id } shouldBe listOf(longer.id)
            }

            Then("남은 이력까지 만료되면 그때 풀린다") {
                scheduler.releaseExpiredBefore(Instant.now().plus(28, DAYS))

                statusOf(member.id) shouldBe Member.Status.ACTIVE
                suspendRepo.findOpen(member.id).shouldBeEmpty()
            }
        }

        /** 무기한 정지는 `releaseAt` 이 null 이라 만료 조회에 안 잡힌다. "아직 유효" 판정에서 빠뜨리기 쉽다. */
        Given("기간 정지와 무기한 정지가 함께 열려 있고 기간 쪽만 만료됐으면") {
            val member = newMember("overlap-forever", Member.Status.SUSPENDED)
            openHistory(member.id, Instant.now().minus(1, DAYS))
            val forever = openHistory(member.id, releaseAt = null)

            scheduler.releaseExpiredBefore(Instant.now())

            Then("정지가 유지된다") {
                statusOf(member.id) shouldBe Member.Status.SUSPENDED
            }

            Then("만료된 이력은 닫고 무기한 이력은 그대로 둔다") {
                suspendRepo.findOpen(member.id).map { it.id } shouldBe listOf(forever.id)
            }

            Then("아무리 시간이 지나도 배치가 풀지 않는다 (사람이 풀어야 한다)") {
                scheduler.releaseExpiredBefore(Instant.now().plus(3_650, DAYS))

                statusOf(member.id) shouldBe Member.Status.SUSPENDED
            }
        }
    }
}

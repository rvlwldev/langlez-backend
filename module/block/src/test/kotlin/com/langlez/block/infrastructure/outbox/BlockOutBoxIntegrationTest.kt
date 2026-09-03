package com.langlez.block.infrastructure.outbox

import com.langlez.block.application.BlockService
import com.langlez.block.infrastructure.jpa.BlockOutBoxRepository
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.rdb.outbox.OutBox
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.every
import io.mockk.mockk
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit.DAYS
import java.util.concurrent.CompletableFuture

/**
 * 차단 → 아웃박스 기록 → 카프카 발행 → 이력 이관까지의 실제 경로.
 *
 * **차단 시 팔로우 해제가 이 경로에 통째로 걸려 있다.** 아웃박스 행이 안 남으면
 * `member-blocked` 가 나가지 않고, 그러면 차단해 놓고 상대 팔로잉 목록에 그대로 남는다.
 * 받는 쪽은 `FollowBlockedIntegrationTest`(follow 모듈)가 본다.
 *
 * 카프카 브로커는 띄우지 않는다. 여기서 확인할 것은 브로커 왕복이 아니라
 * "스케줄러가 실제로 아웃박스를 비우는가" 라서, `KafkaTemplate` 만 대역으로 바꾼다.
 * 스케줄러 빈을 직접 주입받아 부르므로 `@DistributedLock` 어드바이스는 그대로 탄다.
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
@Import(BlockOutBoxIntegrationTest.StubbedKafka::class)
class BlockOutBoxIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: BlockService

    @Autowired
    lateinit var members: MemberRepository

    @Autowired
    lateinit var outbox: BlockOutBoxRepository

    @Autowired
    internal lateinit var scheduler: BlockOutBoxScheduler

    @Autowired
    internal lateinit var historyScheduler: BlockOutBoxHistoryScheduler

    @Autowired
    internal lateinit var cleanupScheduler: BlockOutBoxHistoryCleanupScheduler

    @Autowired
    lateinit var kafka: KafkaTemplate<String, String>

    @Autowired
    lateinit var jdbc: JdbcTemplate

    /** 브로커 없이 발행 성공/실패를 마음대로 만들기 위한 대역. */
    @TestConfiguration
    class StubbedKafka {
        @Bean
        @Primary
        // relaxed 여야 한다. KafkaTemplate 은 BeanNameAware 라 컨텍스트가 setBeanName 을 먼저 부르고,
        // strict 목은 거기서 "no answer found" 로 컨텍스트를 통째로 떨어뜨린다.
        fun stubbedKafkaTemplate(): KafkaTemplate<String, String> = mockk(relaxed = true)
    }

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

    private fun newMember(): Member {
        sequence++
        return members.save(
            Member(
                email = "blockoutbox$sequence@test.com",
                handle = "blockoutbox$sequence",
                provider = Member.Provider.GOOGLE,
                providerId = "blockoutbox$sequence",
            )
        )
    }

    private fun kafkaSucceeds() {
        every { kafka.send(any<ProducerRecord<String, String>>()) } returns
            CompletableFuture.completedFuture(
                SendResult(ProducerRecord("member-blocked", "k", "v"), mockk<RecordMetadata>(relaxed = true))
            )
    }

    init {
        Given("한 회원이 다른 회원을 차단하면") {
            val blocker = newMember()
            val blocked = newMember()

            When("차단이 커밋되면") {
                // Then 마다 도는 beforeEach 를 쓰면 이 When 이 만든 행까지 지운다. 컨테이너 진입 시 한 번만 비운다.
                outbox.deleteAll()
                kafkaSucceeds()
                service.block(blocker.id, blocked.id)

                // BEFORE_COMMIT 리스너라 차단 행과 아웃박스 행이 한 트랜잭션에 묶인다.
                // AFTER_COMMIT 이었다면 그 사이 장애로 이벤트만 사라지고 팔로우가 영영 안 끊긴다.
                Then("member-blocked 아웃박스 행이 PENDING 으로 남는다") {
                    val row = outbox.findAll().single()

                    row.topic shouldBe "member-blocked"
                    row.status shouldBe OutBox.Status.PENDING
                    row.domain shouldBe "BLOCK"
                    // 같은 사람에 대한 이벤트 순서를 지키려면 키가 차단당한 쪽이어야 한다.
                    row.key shouldBe blocked.id.toString()
                    // 컨슈머 멱등 키다. 빠지면 재차단 수습이 재배달과 구분되지 않는다.
                    row.payload!! shouldContain "\"occurredAt\":"
                }

                Then("스케줄러가 돌면 발행되고 COMPLETE 로 바뀐다") {
                    scheduler.send()

                    outbox.findAll().single().status shouldBe OutBox.Status.COMPLETE
                }
            }
        }

        /**
         * 과거에 반쪽만 끊긴 팔로우를 수습하는 경로다.
         * 여기서 행이 안 남으면 그 수습이 통째로 불가능해진다.
         */
        Given("이미 차단한 상대를 다시 차단하면") {
            val blocker = newMember()
            val blocked = newMember()

            When("두 번 호출하면") {
                outbox.deleteAll()
                kafkaSucceeds()

                service.block(blocker.id, blocked.id)
                service.block(blocker.id, blocked.id)

                Then("차단 행은 하나지만 아웃박스 행은 둘이다") {
                    jdbc.queryForObject(
                        "select count(*) from member_blocks where blocker_id = ? and blocked_id = ?",
                        Int::class.java, blocker.id, blocked.id,
                    ) shouldBe 1

                    outbox.findAll() shouldHaveSize 2
                }
            }
        }

        Given("발행이 끝난 아웃박스 행이 남아 있으면") {
            val blocker = newMember()
            val blocked = newMember()

            When("이력 스케줄러가 돌면") {
                outbox.deleteAll()
                kafkaSucceeds()
                service.block(blocker.id, blocked.id)
                scheduler.send()

                val before = jdbc.queryForObject(
                    "select count(*) from block_event_outbox_history", Int::class.java
                )!!

                historyScheduler.archive()

                Then("원본 테이블은 비고 이력 테이블로 옮겨진다") {
                    outbox.findAll().shouldBeEmpty()

                    jdbc.queryForObject(
                        "select count(*) from block_event_outbox_history", Int::class.java
                    ) shouldBe before + 1
                }
            }
        }

        Given("보존 기간 경계에 걸친 이력 행들이 있을 때") {
            // id 가 IDENTITY 가 아니라 직접 채번한다 (OutBoxHistory.id 는 OutBox.id 를 그대로 복사하는 구조).
            jdbc.update("delete from block_event_outbox_history")

            val cutoff = Instant.now().minus(cleanupScheduler.retentionDays, DAYS)

            fun insertHistoryRow(id: Long, createdAt: Instant) = jdbc.update(
                """insert into block_event_outbox_history
                   (id, domain, topic, payload, "key", tries, status, created_at, completed_at)
                   values (?, 'BLOCK', 'member-blocked', '{}', null, 1, 'COMPLETE', ?, ?)""",
                id, java.sql.Timestamp.from(createdAt), java.sql.Timestamp.from(createdAt)
            )

            val expired = 990001L
            val onCutoff = 990002L
            val fresh = 990003L

            insertHistoryRow(expired, cutoff.minusSeconds(60)) // 보존 기간을 막 지남 → 지워져야 한다
            insertHistoryRow(onCutoff, cutoff) // 딱 기준 시각 → findAllByCreatedAtBefore 는 strict < 라 남아야 한다
            insertHistoryRow(fresh, Instant.now()) // 최근 행 → 당연히 남아야 한다

            When("정리 스케줄러가 돌면") {
                // clean() 이 아니라 cleanBefore(cutoff) 를 부른다. clean() 은 자기 Instant.now() 로
                // cutoff 를 다시 계산해서, 여기서 잡은 cutoff 보다 몇 밀리초 뒤가 된다 —
                // 그러면 경계 행이 strict `<` 에 걸려 지워지고 경계 검증 자체가 불가능해진다.
                cleanupScheduler.cleanBefore(cutoff)

                Then("보존 기간을 지난 행만 지워지고 경계·최근 행은 남는다") {
                    val remaining = jdbc.queryForList(
                        "select id from block_event_outbox_history where id >= 990001 order by id",
                        Long::class.java
                    )

                    remaining shouldBe listOf(onCutoff, fresh)
                }
            }
        }

        Given("스케줄러 빈은") {

            Then("AOP 프록시여야 @DistributedLock 이 실제로 걸린다") {
                // 클래스가 final 이면(kotlin-spring 플러그인이 @Component 를 안 열어주면)
                // 프록시가 안 만들어지고 락 없이 그냥 돈다. 조용히 중복 발행된다.
                AopUtils.isAopProxy(scheduler) shouldBe true
                AopUtils.isAopProxy(historyScheduler) shouldBe true
                AopUtils.isAopProxy(cleanupScheduler) shouldBe true
            }
        }
    }
}

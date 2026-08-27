package com.langlez.relationship.infrastructure.outbox

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.rdb.outbox.OutBox
import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.infrastructure.jpa.RelationshipOutBoxRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
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
import java.util.concurrent.CompletableFuture

/**
 * 팔로우 → 아웃박스 기록 → 카프카 발행 → 이력 이관까지의 실제 경로.
 *
 * 카프카 브로커는 띄우지 않는다. 여기서 확인할 것은 브로커 왕복이 아니라
 * "스케줄러가 실제로 아웃박스를 비우는가" 라서, `KafkaTemplate` 만 대역으로 바꾼다.
 * 스케줄러 빈을 직접 주입받아 부르므로 `@DistributedLock` 어드바이스는 그대로 탄다 —
 * 락이 조용히 안 걸리면(빈이 프록시가 아니면) 여기서 드러난다.
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
@Import(RelationshipOutBoxIntegrationTest.StubbedKafka::class)
class RelationshipOutBoxIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: RelationshipService

    @Autowired
    lateinit var members: MemberRepository

    @Autowired
    lateinit var outbox: RelationshipOutBoxRepository

    @Autowired
    internal lateinit var scheduler: RelationshipOutBoxScheduler

    @Autowired
    internal lateinit var historyScheduler: RelationshipOutBoxHistoryScheduler

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
        // send() 는 테스트마다 명시로 스텁하므로 relaxed 가 결과를 조용히 초록으로 만들지는 않는다.
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
                email = "outbox$sequence@test.com",
                handle = "outbox$sequence",
                provider = Member.Provider.GOOGLE,
                providerId = "outbox$sequence",
            )
        )
    }

    private fun kafkaSucceeds() {
        every { kafka.send(any<ProducerRecord<String, String>>()) } returns
            CompletableFuture.completedFuture(
                SendResult(ProducerRecord("member-followed", "k", "v"), mockk<RecordMetadata>(relaxed = true))
            )
    }

    init {
        Given("한 회원이 다른 회원을 팔로우하면") {
            val follower = newMember()
            val followed = newMember()

            When("팔로우가 커밋되면") {
                // Then 마다 도는 beforeEach 를 쓰면 이 When 이 만든 행까지 지운다. 컨테이너 진입 시 한 번만 비운다.
                outbox.deleteAll()
                kafkaSucceeds()
                service.follow(follower.id, followed.id)

                Then("member-followed 아웃박스 행이 PENDING 으로 남는다") {
                    val row = outbox.findAll().single()

                    row.topic shouldBe "member-followed"
                    row.status shouldBe OutBox.Status.PENDING
                    // 컨슈머 멱등 키다. 빠지면 재팔로우가 중복 배달과 구분되지 않는다.
                    row.payload!! shouldContain "\"followId\":"
                }

                Then("스케줄러가 돌면 발행되고 COMPLETE 로 바뀐다") {
                    scheduler.send()

                    outbox.findAll().single().status shouldBe OutBox.Status.COMPLETE
                }
            }
        }

        Given("카프카 발행이 실패하면") {
            val follower = newMember()
            val followed = newMember()

            When("스케줄러가 한 번 돌면") {
                outbox.deleteAll()
                kafkaSucceeds()
                service.follow(follower.id, followed.id)

                every { kafka.send(any<ProducerRecord<String, String>>()) } throws IllegalStateException("브로커 없음")
                scheduler.send()

                Then("COMPLETE 가 되지 않고 PENDING 으로 남아 다음 주기에 다시 시도된다") {
                    val row = outbox.findAll().single()

                    row.status shouldBe OutBox.Status.PENDING
                    row.tries shouldBe 1
                }

                Then("브로커가 살아나면 그다음 주기에 발행된다") {
                    kafkaSucceeds()
                    scheduler.send()

                    outbox.findAll().single().status shouldBe OutBox.Status.COMPLETE
                }
            }
        }

        Given("발행이 끝난 아웃박스 행이 남아 있으면") {
            val follower = newMember()
            val followed = newMember()

            When("이력 스케줄러가 돌면") {
                outbox.deleteAll()
                kafkaSucceeds()
                service.follow(follower.id, followed.id)
                scheduler.send()

                val before = jdbc.queryForObject(
                    "select count(*) from relationship_event_outbox_history", Int::class.java
                )!!

                historyScheduler.archive()

                Then("원본 테이블은 비고 이력 테이블로 옮겨진다") {
                    outbox.findAll().shouldBeEmpty()

                    jdbc.queryForObject(
                        "select count(*) from relationship_event_outbox_history", Int::class.java
                    ) shouldBe before + 1
                }
            }
        }

        Given("스케줄러 빈은") {

            Then("AOP 프록시여야 @DistributedLock 이 실제로 걸린다") {
                // 클래스가 final 이면(kotlin-spring 플러그인이 @Component 를 안 열어주면)
                // 프록시가 안 만들어지고 락 없이 그냥 돈다. 조용히 중복 발행된다.
                AopUtils.isAopProxy(scheduler) shouldBe true
                AopUtils.isAopProxy(historyScheduler) shouldBe true
            }
        }
    }
}

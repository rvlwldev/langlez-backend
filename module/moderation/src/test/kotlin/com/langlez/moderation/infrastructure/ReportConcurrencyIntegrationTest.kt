package com.langlez.moderation.infrastructure

import com.langlez.moderation.application.ReportService
import com.langlez.moderation.domain.Report
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import javax.sql.DataSource

/**
 * 같은 신고를 동시에 넣어도 행이 하나만 남는지 본다.
 *
 * `ReportService.report` 의 `exists` 검사는 check-then-insert 라
 * 두 스레드가 같은 순간에 검사를 통과하면 둘 다 저장한다. 앱에서 못 막는 경합이고
 * **UNQ_REPORT_IDENTITY(V7) 만이 최종 방어선이다.**
 *
 * 행을 남기는 스펙이라 일반 CRUD 스펙과 파일을 나눈다.
 * 시작 시점을 맞추는 데 `Thread.sleep` 대신 `CyclicBarrier` 를 쓴다 —
 * 잠들었다 깨는 시각은 못 맞추지만 배리어는 마지막 스레드가 도달한 순간을 정확히 맞춘다.
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
class ReportConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: ReportService

    @Autowired
    lateinit var dataSource: DataSource

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

    private fun countReports(reporterId: Long): Int = dataSource.connection.use { connection ->
        connection.prepareStatement("select count(*) from reports where reporter_id = ?").use { statement ->
            statement.setLong(1, reporterId)
            statement.executeQuery().use { rs -> rs.next(); rs.getInt(1) }
        }
    }

    /** 두 스레드가 같은 신고를 동시에 접수한다. 실패(예외)는 그대로 모아서 돌려준다. */
    private fun reportTwiceInParallel(reporterId: Long, triggerMessageId: String?): List<Throwable> {
        val barrier = CyclicBarrier(2)
        val failures = java.util.Collections.synchronizedList(mutableListOf<Throwable>())

        val threads = (1..2).map {
            Thread.ofVirtual().unstarted {
                barrier.await(10, TimeUnit.SECONDS)
                runCatching {
                    service.report(
                        reporterId = reporterId,
                        reportedUserId = 999L,
                        sourceType = Report.SourceType.CHAT_USER,
                        sourceId = "77",
                        reason = "욕설",
                        triggerMessageId = triggerMessageId,
                    )
                }.onFailure(failures::add)
            }
        }

        threads.forEach(Thread::start)
        threads.forEach { it.join(30_000) }

        return failures
    }

    init {
        Given("같은 신고를 두 스레드가 동시에 접수하면") {

            When("트리거 메시지가 있는 채팅 신고면") {
                val failures = reportTwiceInParallel(reporterId = 8001L, triggerMessageId = "m7")

                Then("행이 하나만 남는다") {
                    countReports(8001L) shouldBe 1
                }

                Then("어느 쪽도 예외를 밖으로 내보내지 않는다 (중복 신고는 정상 상황이다)") {
                    failures shouldBe emptyList()
                }
            }

            /**
             * Postgres 유니크 인덱스는 기본적으로 NULL 을 서로 다른 값으로 본다.
             * 그대로 두면 `(신고자, 종류, 출처, NULL)` 을 몇 번이든 넣을 수 있어 제약이 무력해진다.
             * V7 이 `nulls not distinct` 로 그 구멍을 닫았는지 확인한다.
             */
            When("트리거 메시지가 없는 신고면") {
                val failures = reportTwiceInParallel(reporterId = 8002L, triggerMessageId = null)

                Then("NULL 이어도 행이 하나만 남는다") {
                    countReports(8002L) shouldBe 1
                }

                Then("어느 쪽도 예외를 밖으로 내보내지 않는다") {
                    failures shouldBe emptyList()
                }
            }
        }
    }
}

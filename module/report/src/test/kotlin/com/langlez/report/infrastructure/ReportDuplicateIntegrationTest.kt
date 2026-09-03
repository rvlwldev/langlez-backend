package com.langlez.report.infrastructure

import com.langlez.report.api.ReportConsumer
import com.langlez.report.application.ReportService
import com.langlez.report.domain.Report
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.sql.SQLException
import javax.sql.DataSource

/**
 * 신고 중복 방어가 무엇을 막고 무엇을 통과시키는지 고정한다.
 *
 * 겹이 셋이다 — `MessageDeduplicator`(레디스), `exists`(앱), `UNQ_REPORT_IDENTITY`(DB).
 * 앞의 둘이 뚫리는 경로가 실재하므로(레디스 장애 fail-open, TTL 만료, 페이로드가 다른 재전달,
 * 그리고 check-then-insert 경합) 마지막 겹의 판정 범위를 여기서 못 박는다.
 *
 * 제약 자체는 JDBC 로 직접 찌른다. 서비스를 거치면 `exists` 가 먼저 걸러서
 * 인덱스가 없어도 통과하는 단언이 된다 — 그러면 아무것도 검증하지 못한다.
 * 경합 상황에서의 동작은 [ReportConcurrencyIntegrationTest] 가 본다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 브로커가 없는 테스트다. 컨슈머 메서드를 직접 부르므로 리스너를 띄울 이유가 없다.
        "spring.kafka.listener.auto-startup=false",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class ReportDuplicateIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: ReportService

    @Autowired
    lateinit var consumer: ReportConsumer

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

    /** 앱 계층의 검사를 건너뛰고 제약만 찌른다. */
    private fun insert(reporterId: Long, sourceId: String, triggerMessageId: String?) =
        dataSource.connection.use { connection ->
            connection.prepareStatement(
                "insert into reports " +
                    "(reporter_id, reported_user_id, source_type, source_id, reason, trigger_message_id, created_at) " +
                    "values (?, 999, 'CHAT_USER', ?, '욕설', ?, now())"
            ).use { statement ->
                statement.setLong(1, reporterId)
                statement.setString(2, sourceId)
                statement.setString(3, triggerMessageId)
                statement.executeUpdate()
            }
        }

    init {
        /**
         * Postgres 유니크 인덱스는 기본적으로 NULL 을 서로 다른 값으로 본다.
         * 그대로 두면 `(신고자, 종류, 출처, NULL)` 을 몇 번이든 넣을 수 있어 제약이 무력해진다 —
         * 게시글 신고는 트리거 메시지가 항상 NULL 이라 전부 이 구멍으로 빠진다.
         */
        Given("트리거 메시지가 NULL 인 같은 신고를 DB 에 두 번 넣으면") {
            insert(reporterId = 8101L, sourceId = "91", triggerMessageId = null)

            Then("두 번째 삽입이 UNQ_REPORT_IDENTITY 로 거부된다") {
                val ex = shouldThrow<SQLException> { insert(8101L, "91", null) }

                ex.message!!.lowercase() shouldContain "unq_report_identity"
            }

            Then("행은 하나뿐이다") {
                countReports(8101L) shouldBe 1
            }
        }

        Given("트리거 메시지가 있는 같은 신고를 DB 에 두 번 넣으면") {
            insert(reporterId = 8102L, sourceId = "92", triggerMessageId = "m1")

            Then("두 번째 삽입이 거부된다") {
                val ex = shouldThrow<SQLException> { insert(8102L, "92", "m1") }

                ex.message!!.lowercase() shouldContain "unq_report_identity"
            }
        }

        /**
         * 제약을 너무 좁게 잡으면 정당한 신고가 막힌다. 그건 중복이 쌓이는 것보다 나쁘다 —
         * 사용자는 신고했다고 믿는데 운영자에게는 아무것도 안 간다.
         */
        Given("같은 사람이 같은 상대를 다시 신고할 때") {
            insert(reporterId = 8103L, sourceId = "93", triggerMessageId = "m1")

            Then("다른 방/다른 글에서의 신고는 저장된다") {
                insert(reporterId = 8103L, sourceId = "94", triggerMessageId = "m1")

                countReports(8103L) shouldBe 2
            }

            Then("같은 방이어도 다른 메시지를 집으면 저장된다") {
                insert(reporterId = 8103L, sourceId = "93", triggerMessageId = "m2")

                countReports(8103L) shouldBe 3
            }
        }

        Given("서로 다른 두 사람이 같은 지점을 신고하면") {
            Then("둘 다 저장된다") {
                insert(reporterId = 8104L, sourceId = "93", triggerMessageId = "m1")
                insert(reporterId = 8105L, sourceId = "93", triggerMessageId = "m1")

                countReports(8104L) shouldBe 1
                countReports(8105L) shouldBe 1
            }
        }

        /**
         * 필드 순서만 다른 두 페이로드. 역직렬화하면 같은 이벤트지만 dedup 해시는 다르다 —
         * `MessageDeduplicator` 가 통과시키는 실제 경로를 그대로 재현한다.
         */
        Given("같은 신고가 JSON 만 다른 두 레코드로 배달되면") {
            val first = """{"roomId":95,"reporterId":8106,"reportedUserId":999,""" +
                """"reason":"욕설","triggerMessageId":"m1"}"""
            val second = """{"reporterId":8106,"roomId":95,"triggerMessageId":"m1",""" +
                """"reportedUserId":999,"reason":"욕설"}"""

            consumer.onChatUserReported(first)

            Then("두 번째 배달도 예외 없이 끝난다 (예외를 올리면 오프셋이 안 넘어가고 DLT 로 간다)") {
                consumer.onChatUserReported(second)
            }

            Then("행은 하나뿐이다") {
                countReports(8106L) shouldBe 1
            }
        }

        Given("이미 접수된 신고를 HTTP 로 다시 보내면") {
            service.report(8107L, 999L, Report.SourceType.ECHO_POST, "96", "욕설")

            Then("예외 없이 끝난다 (두 번 눌러도 204 여야 한다)") {
                service.report(8107L, 999L, Report.SourceType.ECHO_POST, "96", "욕설")

                countReports(8107L) shouldBe 1
            }
        }
    }
}

package com.langlez.member.infrastructure.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import javax.sql.DataSource

/**
 * 아웃박스 발행 폴링(`OutBoxRepository.fetch`)이 V17 의 부분 인덱스를 실제로 타는지 본다.
 *
 * 이 쿼리는 2초마다 무조건 돈다. 인덱스가 빠지면 장애가 아니라 "낮에만 느려지는" 형태로 나타나서
 * 아무도 못 본다 — 아카이버가 06:00 에만 돌아 COMPLETE 행이 하루치 쌓이기 때문이다.
 * 그래서 존재 여부가 아니라 실행 계획을 본다.
 *
 * **부분 인덱스라 특히 조심해야 한다.** 플래너는 술어를 증명해야 부분 인덱스를 쓴다.
 * 지금은 Hibernate 가 `status='PENDING'` 을 SQL 에 상수로 인라인해서 증명이 되지만,
 * 이 조건이 바인드 파라미터로 바뀌면 인덱스가 조용히 안 쓰인다 — 컴파일도 다른 테스트도
 * 전부 통과한다. 여기가 그걸 잡는 유일한 자리다.
 *
 * 나머지 4개 테이블(chat/echo/follow/block)은 같은 V17 파일이 같은 DDL 로 만드는 복제라
 * 대표로 member 만 본다.
 *
 * JPA 가 아니라 JDBC 로 직접 쏜다. `analyze`/`explain` 은 JPA 트랜잭션 규칙과 안 맞고
 * 여기서 볼 것은 매핑이 아니라 실행 계획이다 (FollowIndexIntegrationTest 와 같은 이유).
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
class MemberOutBoxIndexIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var dataSource: DataSource

    companion object {
        /** 아카이브 전까지 쌓이는 처리 완료 행. PENDING 이 여기 묻혀야 실제 상황과 같다. */
        private const val COMPLETED_ROWS = 20000

        private const val PENDING_ROWS = 200

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

    private fun execute(sql: String) = dataSource.connection.use { it.createStatement().execute(sql) }

    private fun query(sql: String): List<String> = dataSource.connection.use { connection ->
        connection.createStatement().executeQuery(sql).use { rs ->
            generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
        }
    }

    private fun explain(sql: String) = query("explain $sql").joinToString("\n").lowercase()

    init {
        Given("아카이브되지 않은 처리 완료 행이 쌓이고 그 사이에 발행 대기 행이 섞이면") {
            execute(
                "insert into member_event_outbox (domain, topic, payload, status, tries, created_at, completed_at) " +
                    "select 'member', 'member-created', 'payload ' || g, 'COMPLETE', 0, " +
                    "now() - (g || ' seconds')::interval, now() from generate_series(1, $COMPLETED_ROWS) g"
            )
            execute(
                "insert into member_event_outbox (domain, topic, payload, status, tries, created_at) " +
                    "select 'member', 'member-created', 'payload ' || g, 'PENDING', 0, " +
                    "now() - (g || ' seconds')::interval from generate_series(1, $PENDING_ROWS) g"
            )
            execute("vacuum analyze member_event_outbox")

            /**
             * `OutBoxRepository.findAllByStatusAndTriesLessThanEqualOrderByCreatedAtAsc` 가 만드는 SQL 이다.
             * `for update skip locked` 는 계획에 LockRows 노드만 더할 뿐 스캔 방식을 바꾸지 않아
             * 그대로 둔 채 본다.
             */
            Then("발행 폴링이 부분 인덱스를 타고 Seq Scan 을 하지 않는다") {
                val plan = explain(
                    "select * from member_event_outbox where status = 'PENDING' and tries <= 3 " +
                        "order by created_at asc limit 1000 for update skip locked"
                )

                plan shouldContain "idx_member_event_outbox_pending"
                plan shouldNotContain "seq scan on member_event_outbox"
            }

            /**
             * 인덱스를 타는 것만으로는 부족하다. created_at 을 인덱스에 담은 이유가 정렬을 넘겨받는
             * 것이라, Sort 노드가 남아 있으면 chunk 를 키울수록 다시 무거워진다.
             */
            Then("정렬을 인덱스가 제공해 Sort 노드가 남지 않는다") {
                val plan = explain(
                    "select * from member_event_outbox where status = 'PENDING' and tries <= 3 " +
                        "order by created_at asc limit 1000 for update skip locked"
                )

                plan shouldNotContain "sort key"
            }
        }
    }
}

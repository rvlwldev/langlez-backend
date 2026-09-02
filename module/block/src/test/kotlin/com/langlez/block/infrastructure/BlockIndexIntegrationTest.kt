package com.langlez.block.infrastructure

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
 * 차단 조회가 실제로 인덱스를 타는지 EXPLAIN 으로 확인한다.
 *
 * 인덱스 존재 여부만 보면 "있는데 안 쓰는" 경우를 놓친다 — 선두 컬럼이 아닌 조건은
 * 인덱스가 있어도 full scan 이 된다.
 *
 * 플래너는 테이블이 작으면 인덱스가 있어도 seq scan 을 고르므로, 행을 충분히 넣고
 * ANALYZE 로 통계를 갱신한 뒤에 본다. 행을 대량으로 남기는 스펙이라
 * 페이징 검증과 같은 클래스에 두지 않는다.
 *
 * JPA 가 아니라 JDBC 로 직접 쏜다. `analyze` 와 `explain` 은 JPA 의 트랜잭션 규칙에 걸리고
 * (`executeUpdate` 는 트랜잭션을 요구한다) 여기서 검증할 것은 매핑이 아니라 실행 계획이다.
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
class BlockIndexIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var dataSource: DataSource

    companion object {
        /**
         * 플래너가 인덱스를 고를 만큼 넣되, 대상 회원의 행이 테이블의 극히 일부여야 한다.
         * 한 회원이 테이블 절반을 차지하면 seq scan 이 실제로 옳은 계획이라 인덱스 유무와 무관하게 안 탄다.
         */
        private const val NOISE_ROWS = 20000

        private const val STAR_ROWS = 300

        private const val STAR = 9001L

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
        Given("차단 행이 충분히 쌓이고 통계가 갱신되면") {
            execute(
                "insert into member_blocks (blocker_id, blocked_id, created_at) " +
                    "select 100000 + g, 300000 + (g % 997), now() from generate_series(1, $NOISE_ROWS) g"
            )
            execute(
                "insert into member_blocks (blocker_id, blocked_id, created_at) " +
                    "select $STAR, 600000 + g, now() from generate_series(1, $STAR_ROWS) g"
            )
            execute("vacuum analyze member_blocks")

            // 차단 목록도 커서 페이징이라 전용 인덱스가 없으면 PK 역순 훑기로 빠진다.
            Then("차단 목록 조회가 전용 인덱스를 탄다") {
                val plan = explain(
                    "select id, blocked_id from member_blocks " +
                        "where blocker_id = $STAR and id < 9223372036854775807 order by id desc limit 20"
                )

                plan shouldContain "idx_member_block_blocker"
                plan shouldNotContain "member_blocks_pkey"
            }

            /**
             * 양방향 차단 판정은 인자를 뒤집어 부를 뿐 두 컬럼 모두 등치라
             * 어느 방향이든 UNQ_MEMBER_BLOCK 선두 컬럼을 탄다. 여기엔 새 인덱스가 필요 없다.
             */
            Then("양방향 차단 판정은 기존 유니크 인덱스로 충분하다") {
                val plan = explain(
                    "select 1 from member_blocks where blocker_id = $STAR and blocked_id = 600001 limit 1"
                )

                plan shouldContain "unq_member_block"
                plan shouldNotContain "seq scan on member_blocks"
            }
        }
    }
}

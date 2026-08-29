package com.langlez.rdb.search

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

/**
 * 3자 이상 검색어가 실제로 함수 인덱스(Bitmap Index Scan)를 타는지 EXPLAIN 으로 확인한다.
 * 행이 적으면 플래너가 인덱스가 있어도 Seq Scan 을 고르므로 노이즈 행을 충분히 넣고
 * ANALYZE 로 통계를 갱신한 뒤 플래너가 실제로 무엇을 고르는지 본다
 * (enable_seqscan=off 로 강제하지 않는다 - 그러면 "탈 수 있다"만 보이고 "고른다"는 안 보인다).
 *
 * JPA 가 아니라 JDBC 로 직접 쏜다. explain/vacuum 은 JPA 트랜잭션 규칙과 안 맞고
 * 여기서 검증할 것은 매핑이 아니라 실행 계획이다 (FollowIndexIntegrationTest 와 같은 이유).
 *
 * 행을 대량으로 남기는 스펙이라 TrgmSearchFunctionTest 와 클래스를 분리한다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.main.allow-bean-definition-overriding=true",
    ]
)
class TrgmSearchIndexScanTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var dataSource: DataSource

    companion object {
        private const val NOISE_ROWS = 50000

        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
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
        Given("검색 컬럼에 함수 인덱스를 걸고 행이 충분히 쌓이면") {
            execute(
                "create index idx_search_spike_content_trgm on search_spike_documents " +
                    "using gin (f_unaccent(content) gin_trgm_ops)"
            )
            execute(
                "insert into search_spike_documents (content) " +
                    "select 'noise text number ' || g from generate_series(1, $NOISE_ROWS) g"
            )
            execute("insert into search_spike_documents (content) values ('there is an apple on the table')")
            execute("vacuum analyze search_spike_documents")

            Then("3자 이상 검색어(ppl)는 Bitmap Index Scan 을 탄다") {
                val plan = explain(
                    "select id from search_spike_documents " +
                        "where f_unaccent(content) like f_unaccent('%ppl%') escape '\\'"
                )

                plan shouldContain "idx_search_spike_content_trgm"
                plan shouldContain "bitmap index scan"
            }
        }
    }
}

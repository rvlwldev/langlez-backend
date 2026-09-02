package com.langlez.follow.infrastructure

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
 * 팔로우 조회가 실제로 인덱스를 타는지 EXPLAIN 으로 확인한다.
 *
 * 인덱스 존재 여부만 보면 "있는데 안 쓰는" 경우를 놓친다 — 선두 컬럼이 아닌 조건은
 * 인덱스가 있어도 full scan 이 된다. UNQ_MEMBER_FOLLOW(follower_id, followed_id) 만
 * 있던 시절 팔로워 조회가 정확히 그 상태였다.
 *
 * 플래너는 테이블이 작으면 인덱스가 있어도 seq scan 을 고르므로, 행을 충분히 넣고
 * ANALYZE 로 통계를 갱신한 뒤에 본다. 행을 대량으로 남기는 스펙이라
 * 페이징·카운트 검증과 같은 클래스에 두지 않는다.
 *
 * JPA 가 아니라 JDBC 로 직접 쏜다. `analyze` 와 `explain` 은 JPA 의 트랜잭션 규칙에 걸리고
 * (`executeUpdate` 는 트랜잭션을 요구한다) 여기서 검증할 것은 매핑이 아니라 실행 계획이다.
 *
 * 차단 인덱스는 `BlockIndexIntegrationTest` 가 같은 방식으로 본다.
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
class FollowIndexIntegrationTest : BehaviorSpec() {

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
        Given("팔로우 행이 충분히 쌓이고 통계가 갱신되면") {
            // 남들끼리의 팔로우. 대상 회원의 행이 묻힐 만큼 깔아둔다.
            execute(
                "insert into member_follows (follower_id, followed_id, created_at) " +
                    "select 100000 + g, 300000 + (g % 997), now() from generate_series(1, $NOISE_ROWS) g"
            )
            // 대상 회원의 팔로워 + 팔로잉. 두 방향을 같은 규모로 본다.
            execute(
                "insert into member_follows (follower_id, followed_id, created_at) " +
                    "select 400000 + g, $STAR, now() from generate_series(1, $STAR_ROWS) g"
            )
            execute(
                "insert into member_follows (follower_id, followed_id, created_at) " +
                    "select $STAR, 500000 + g, now() from generate_series(1, $STAR_ROWS) g"
            )
            // index-only scan 은 visibility map 이 채워져야 선택된다. 통계만 갱신해선 부족하다.
            execute("vacuum analyze member_follows")

            Then("팔로워 목록 조회가 seq scan 이 아니다") {
                val plan = explain(
                    "select id, follower_id from member_follows " +
                        "where followed_id = $STAR and id < 9223372036854775807 order by id desc limit 20"
                )

                plan shouldContain "idx_member_follow_followed"
                plan shouldNotContain "seq scan on member_follows"
            }

            Then("팔로워 카운트도 seq scan 이 아니다") {
                val plan = explain("select count(*) from member_follows where followed_id = $STAR")

                plan shouldContain "idx_member_follow_followed"
                plan shouldNotContain "seq scan on member_follows"
            }

            /**
             * UNQ_MEMBER_FOLLOW(follower_id, followed_id) 만으로는 부족하다.
             * 그 인덱스엔 id 가 없어서 정렬을 못 주고, 플래너는 그럴 바엔 PK 를 역순으로 훑으며
             * follower_id 를 필터링하는 계획을 고른다 — LIMIT 만 보면 싸 보이지만
             * 팔로잉이 적은 회원일수록 20건을 채우려고 테이블을 더 멀리 훑는다.
             */
            Then("팔로잉 목록 조회도 PK 역순 훑기가 아니라 전용 인덱스를 탄다") {
                val plan = explain(
                    "select id, followed_id from member_follows " +
                        "where follower_id = $STAR and id < 9223372036854775807 order by id desc limit 20"
                )

                plan shouldContain "idx_member_follow_follower"
                plan shouldNotContain "member_follows_pkey"
            }

            /**
             * `seq scan 아님` 만 단언하면 V6 없이도 통과한다 — follower_id 가
             * UNQ_MEMBER_FOLLOW 의 선두 컬럼이라 그 유니크 인덱스로도 카운트는 나온다.
             * V6 를 지웠을 때 빨간불이 뜨려면 새 인덱스를 타는 것까지 봐야 한다.
             */
            Then("팔로잉 카운트가 V6 전용 인덱스를 탄다") {
                val plan = explain("select count(*) from member_follows where follower_id = $STAR")

                plan shouldContain "idx_member_follow_follower"
                plan shouldNotContain "seq scan on member_follows"
            }
        }
    }
}

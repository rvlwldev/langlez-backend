package com.langlez.member.infrastructure.outbox

import com.langlez.member.infrastructure.jpa.MemberOutBoxRepository
import com.langlez.rdb.outbox.OutBox
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
 *
 * ### 왜 리터럴 SQL EXPLAIN 으로 끝내지 않는가
 *
 * 이 스펙의 첫 판은 `explain select ... where status = 'PENDING' ...` 을 손으로 써서 확인했고,
 * 초록불이 떴는데도 **프로덕션은 인덱스를 못 타는 상태였다.** 손으로 쓴 SQL 은 상수지만
 * 실제 경로인 파생 쿼리는 `status = ?` 바인드로 나갔기 때문이다. 거짓 양성이었다.
 *
 * P6Spy 로그도 믿을 게 못 된다. `sqlWithValues` 가 `?` 를 값으로 치환해 보여줘서
 * (`P6SpyEventListener.kt`) 바인드인지 리터럴인지 구분이 안 된다 — 그게 실제로 사람을 속였다.
 *
 * 그래서 여기서는 **서버가 받은 SQL** 을 본다 (`postgres -c log_statement=all` 로 컨테이너를 띄우고
 * 컨테이너 로그를 읽는다). 그리고 부분 인덱스가 깨지는 조건인 **generic plan 을 강제**해서
 * 그 상태에서도 인덱스를 타는지 확인한다.
 *
 * 나머지 4개 테이블(chat/echo/follow/block)은 같은 V17 파일이 같은 DDL 로 만들고
 * 같은 `OutBoxRepository` 를 상속하는 복제라 대표로 member 만 본다.
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

    @Autowired
    lateinit var repo: MemberOutBoxRepository

    companion object {
        /** 아카이브 전까지 쌓이는 처리 완료 행. PENDING 이 여기 묻혀야 실제 상황과 같다. */
        private const val COMPLETED_ROWS = 20000

        private const val PENDING_ROWS = 200

        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            // 서버가 실제로 받은 SQL 을 봐야 한다. 클라이언트 쪽 로그는 바인드 값을 채워 보여준다.
            .withCommand("postgres", "-c", "log_statement=all")
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

    /**
     * PREPARE 와 plan_cache_mode 는 세션에 걸린다. 커넥션 풀에서 매번 새로 꺼내면
     * "prepared statement does not exist" 로 터지므로 한 커넥션 안에서 끝낸다.
     */
    private fun explainGeneric(prepare: String, execute: String): String =
        dataSource.connection.use { connection ->
            connection.createStatement().use { it.execute(prepare) }
            connection.createStatement().use { it.execute("set plan_cache_mode = force_generic_plan") }

            connection.createStatement().executeQuery("explain $execute").use { rs ->
                generateSequence { if (rs.next()) rs.getString(1) else null }.toList()
            }.joinToString("\n").lowercase()
                .also { connection.createStatement().use { s -> s.execute("reset plan_cache_mode") } }
        }

    /**
     * 서버 로그에서 폴링 쿼리 줄만 뽑는다. `skip locked` 는 이 쿼리에만 붙는다.
     * 이 스펙이 손으로 쓴 `prepare`/`explain` 은 제외한다 — 그건 Hibernate 가 만든 SQL 이 아니라
     * 우리가 쓴 리터럴이라, 세는 순간 거짓 양성이 된다.
     */
    private fun loggedPollStatements() = postgres.logs.lineSequence()
        .filter { it.contains("member_event_outbox") && it.contains("skip locked", ignoreCase = true) }
        .filterNot { it.contains("prepare ", ignoreCase = true) }
        .filterNot { it.contains("explain ", ignoreCase = true) }
        .toList()

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
             * 프로덕션 경로 그대로다. `@Query` 로 바꾸면서 `#{#entityName}` 치환, `@Transactional`,
             * `@Lock` + SKIP LOCKED 가 전부 살아 있어야 여기가 통과한다.
             */
            Then("실제 리포지토리 호출이 발행 대기 행만 chunk 만큼 가져온다") {
                val fetched = repo.fetch(chunk = 50, maxTries = 3)

                fetched.size shouldBe 50
                fetched.all { it.status == OutBox.Status.PENDING } shouldBe true
            }

            /**
             * **이 스펙의 핵심이다.** status 가 바인드 파라미터로 나가면 플래너가 generic plan 에서
             * `status = $1` 이 부분 인덱스 술어를 함의한다고 증명하지 못해 인덱스를 통째로 버린다.
             * 2초마다 도는 폴러는 pgjdbc prepareThreshold(5)를 즉시 넘겨 서버사이드 PREPARE 로 가므로
             * 이건 가정이 아니라 정상 운영 상태다.
             */
            Then("서버가 받은 폴링 SQL 에 status 가 상수로 박혀 있다") {
                repo.fetch(chunk = 10, maxTries = 3)

                val statements = loggedPollStatements()
                statements.isNotEmpty() shouldBe true

                val sql = statements.joinToString("\n").lowercase()
                sql shouldContain "status='pending'"
                // 바인드로 나가면 여기가 걸린다. `$1` 은 확장 프로토콜, `?` 는 단순 프로토콜.
                sql shouldNotContain "status=\$1"
                sql shouldNotContain "status=?"
            }

            /**
             * 위에서 확인한 모양(status 는 리터럴, tries 만 파라미터)을 그대로 prepare 해
             * **generic plan 을 강제**한다. 아카이브 직후처럼 통계가 인덱스에 불리한 시점에
             * 플래너가 generic plan 을 영구 채택해도 인덱스를 타는지가 관건이다.
             */
            Then("generic plan 을 강제해도 부분 인덱스를 타고 Sort 노드가 남지 않는다") {
                val plan = explainGeneric(
                    prepare = "prepare outbox_poll (int) as " +
                        "select * from member_event_outbox where status = 'PENDING' and tries <= \$1 " +
                        "order by created_at asc limit 1000 for no key update skip locked",
                    execute = "execute outbox_poll(3)",
                )

                plan shouldContain "idx_member_event_outbox_pending"
                plan shouldNotContain "seq scan on member_event_outbox"
                plan shouldNotContain "sort key"
            }

            /**
             * 반대 방향도 못 박아 둔다 — status 를 바인드로 보내면 generic plan 에서 정말로
             * 인덱스를 잃는다. 이게 리뷰가 지적한 결함이고, 여기가 그 회귀를 잡는 자리다.
             */
            Then("status 를 바인드로 보내면 generic plan 이 인덱스를 잃는다") {
                val plan = explainGeneric(
                    prepare = "prepare outbox_poll_bound (varchar, int) as " +
                        "select * from member_event_outbox where status = \$1 and tries <= \$2 " +
                        "order by created_at asc limit 1000 for no key update skip locked",
                    execute = "execute outbox_poll_bound('PENDING', 3)",
                )

                plan shouldContain "seq scan on member_event_outbox"
                plan shouldNotContain "idx_member_event_outbox_pending"
            }

            /** 통계가 정상일 때(custom plan) 실제로 고르는 계획도 본다. */
            Then("custom plan 에서도 부분 인덱스를 고른다") {
                val plan = query(
                    "explain select * from member_event_outbox where status = 'PENDING' and tries <= 3 " +
                        "order by created_at asc limit 1000 for no key update skip locked"
                ).joinToString("\n").lowercase()

                plan shouldContain "idx_member_event_outbox_pending"
                plan shouldNotContain "seq scan on member_event_outbox"
                plan shouldNotContain "sort key"
            }
        }
    }
}

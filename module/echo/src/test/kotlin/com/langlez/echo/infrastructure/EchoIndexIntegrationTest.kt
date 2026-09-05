package com.langlez.echo.infrastructure

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
 * 피드 조회가 V18 의 인덱스를 실제로 타는지 EXPLAIN 으로 확인한다.
 *
 * 인덱스 존재 여부만 보면 "있는데 안 쓰는" 경우를 놓친다. post_hashtags 가 정확히 그랬다 —
 * UNQ_POST_HASHTAG(post_id, hashtag_id) 가 있었지만 해시태그 타임라인은 hashtag_id 로 들어와
 * 인덱스를 통째로 훑거나 아예 seq scan 이 됐다.
 *
 * 플래너는 테이블이 작으면 인덱스가 있어도 seq scan 을 고르므로 노이즈 행을 충분히 넣고
 * ANALYZE 로 통계를 갱신한 뒤에 본다 (enable_seqscan=off 로 강제하지 않는다 —
 * 그러면 "탈 수 있다"만 보이고 "고른다"는 안 보인다).
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
class EchoIndexIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var dataSource: DataSource

    companion object {
        /**
         * 플래너가 인덱스를 고를 만큼 넣되, 대상 회원·글의 행이 테이블의 극히 일부여야 한다.
         * 한 작성자가 테이블 절반을 차지하면 seq scan 이 실제로 옳은 계획이라 인덱스와 무관하게 안 탄다.
         */
        private const val NOISE_ROWS = 20000

        private const val STAR_ROWS = 300

        private const val STAR_AUTHOR = 9001L

        private const val STAR_POST = 9002L

        private const val STAR_TAG = "langlez"

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
        Given("글·댓글·첨부·해시태그가 충분히 쌓이고 통계가 갱신되면") {
            // 남들의 글. 대상 작성자의 글이 묻힐 만큼 깔아둔다.
            execute(
                "insert into posts (blinded, report_count, author_id, created_at, like_count, content) " +
                    "select false, 0, 100000 + (g % 997), now(), 0, 'noise ' || g " +
                    "from generate_series(1, $NOISE_ROWS) g"
            )
            execute(
                "insert into posts (blinded, report_count, author_id, created_at, like_count, content) " +
                    "select false, 0, $STAR_AUTHOR, now(), 0, 'star ' || g " +
                    "from generate_series(1, $STAR_ROWS) g"
            )

            execute(
                "insert into comments (author_id, created_at, post_id, content) " +
                    "select 1, now(), 100000 + (g % 997), 'noise ' || g from generate_series(1, $NOISE_ROWS) g"
            )
            execute(
                "insert into comments (author_id, created_at, post_id, content) " +
                    "select 1, now(), $STAR_POST, 'star ' || g from generate_series(1, $STAR_ROWS) g"
            )

            execute(
                "insert into post_media (sequence, post_id, type, url) " +
                    "select 0, 100000 + (g % 997), 'IMAGE', 'https://cdn.test/' || g " +
                    "from generate_series(1, $NOISE_ROWS) g"
            )
            execute(
                "insert into post_media (sequence, post_id, type, url) " +
                    "select 0, $STAR_POST, 'IMAGE', 'https://cdn.test/star' || g " +
                    "from generate_series(1, $STAR_ROWS) g"
            )

            execute("insert into hashtags (name, created_at) values ('$STAR_TAG', now())")
            execute(
                "insert into hashtags (name, created_at) " +
                    "select 'noise' || g, now() from generate_series(1, 997) g"
            )
            // post_id 는 UNQ_POST_HASHTAG 때문에 태그별로 겹치면 안 된다.
            // 노이즈는 위에서 넣은 노이즈 글(1..NOISE_ROWS), 스타 태그는 스타 작성자의 글을 가리킨다.
            execute(
                "insert into post_hashtags (hashtag_id, post_id) " +
                    "select (select id from hashtags where name = 'noise' || ((g % 997) + 1)), g " +
                    "from generate_series(1, $NOISE_ROWS) g"
            )
            execute(
                "insert into post_hashtags (hashtag_id, post_id) " +
                    "select (select id from hashtags where name = '$STAR_TAG'), $NOISE_ROWS + g " +
                    "from generate_series(1, $STAR_ROWS) g"
            )

            // index-only scan 은 visibility map 이 채워져야 선택된다. 통계만 갱신해선 부족하다.
            execute("vacuum analyze posts")
            execute("vacuum analyze comments")
            execute("vacuum analyze post_media")
            execute("vacuum analyze post_hashtags")
            execute("vacuum analyze hashtags")

            /**
             * `EchoRepositoryImpl.findPosts` 의 SQL 이다. 작성자 하나(회원 타임라인)에서 본다 —
             * 홈 타임라인처럼 author_id in (여럿) 이면 btree 가 전역 id 정렬을 못 만들어 줘서
             * 플래너가 데이터 분포에 따라 PK 역순 스캔을 고를 수도 있고, 그건 오판이 아니다.
             */
            Then("회원 타임라인이 PK 역순 훑기가 아니라 IDX_POST_AUTHOR 를 탄다") {
                val plan = explain(
                    "select * from posts where author_id in ($STAR_AUTHOR) and deleted_at is null " +
                        "and blinded = false and id < 9223372036854775807 order by id desc limit 20"
                )

                plan shouldContain "idx_post_author"
                plan shouldNotContain "posts_pkey"
            }

            Then("댓글 목록이 IDX_COMMENT_POST 를 타고 Seq Scan 을 하지 않는다") {
                val plan = explain(
                    "select * from comments where post_id = $STAR_POST and deleted_at is null " +
                        "and id > 0 order by id asc limit 20"
                )

                plan shouldContain "idx_comment_post"
                plan shouldNotContain "seq scan on comments"
            }

            Then("첨부 조회가 IDX_POST_MEDIA_POST 를 타고 Seq Scan 을 하지 않는다") {
                val plan = explain("select * from post_media where post_id in ($STAR_POST)")

                plan shouldContain "idx_post_media_post"
                plan shouldNotContain "seq scan on post_media"
            }

            /**
             * `EchoRepositoryImpl.findPostsByHashtag` 다. hashtag_id 를 조인으로 받으므로
             * 계획 시점에 값을 모른다 — UNQ_POST_HASHTAG 만 있던 시절 여기가 post_hashtags
             * 전체 스캔이었다. 조인 쿼리 그대로 봐야 그 상태가 재현된다.
             */
            Then("해시태그 타임라인이 IDX_POST_HASHTAG_HASHTAG 를 타고 post_hashtags 를 훑지 않는다") {
                val plan = explain(
                    "select p.* from posts p " +
                        "join post_hashtags ph on ph.post_id = p.id " +
                        "join hashtags h on h.id = ph.hashtag_id and h.name = '$STAR_TAG' " +
                        "where p.deleted_at is null and p.blinded = false order by p.id desc limit 20"
                )

                plan shouldContain "idx_post_hashtag_hashtag"
                plan shouldNotContain "seq scan on post_hashtags"
            }
        }
    }
}

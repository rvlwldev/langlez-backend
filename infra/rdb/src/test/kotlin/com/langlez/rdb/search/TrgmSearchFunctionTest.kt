package com.langlez.rdb.search

import com.langlez.rdb.search.QSearchDocument.Companion.searchDocument as doc
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import javax.sql.DataSource

/**
 * V10 이 만든 f_unaccent + pg_trgm 기반 검색 기계가 실제로 동작하는지 끝까지 검증한다.
 * 호출자가 아직 없어서(컬럼 적용은 다음 작업) 이 통합테스트가 유일한 증거다.
 *
 * search_spike_documents 는 Flyway 가 아니라 이 테스트 컨텍스트가 만드는 스캐폴딩 테이블이라
 * ddl-auto 를 예외적으로 create-drop 으로 둔다 (레포 기본값은 validate).
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
class TrgmSearchFunctionTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var dsl: JPAQueryFactory

    @Autowired
    lateinit var dataSource: DataSource

    companion object {
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

    private fun scalar(sql: String): String? = dataSource.connection.use { connection ->
        connection.createStatement().executeQuery(sql).use { rs -> if (rs.next()) rs.getString(1) else null }
    }

    init {
        Given("V10 마이그레이션이 적용되면") {
            Then("f_unaccent 가 존재하고 발음부호를 지운다") {
                scalar("select f_unaccent('español')") shouldBe "espanol"
            }

            Then("f_unaccent 는 CJK 를 건드리지 않는다") {
                scalar("select f_unaccent('한국어')") shouldBe "한국어"
            }

            // 잘못된 IMMUTABLE 선언이면 여기서 "functions in index expression must be marked IMMUTABLE" 로 실패한다.
            // 이게 그 선언이 맞다는 걸 증명하는 유일한 방법이다.
            Then("f_unaccent 로 GIN 인덱스를 만들 수 있다") {
                execute(
                    "create index idx_search_spike_content_trgm on search_spike_documents " +
                        "using gin (f_unaccent(content) gin_trgm_ops)"
                )
            }
        }

        Given("12개 언어와 여러 표기 패턴의 문서가 쌓이면") {
            val samples = listOf(
                "한국어 공부 같이 하실 분 구해요",
                "日本語を勉強したいです",
                "I want to practice English conversation",
                "Ich möchte Deutsch üben, danke schön",
                "Quiero practicar español, gracias",
                "Je veux pratiquer le français, merci",
                "Quero praticar português, obrigado",
                "Saya ingin belajar bahasa Indonesia",
                "Хочу практиковать русский язык",
                "Tôi muốn học tiếng Việt",
                "我想练习中文口语",
                "我想練習中文口語",
                "한국어공부하실분구해요",
                "there is an apple on the table",
                "me gusta comer queso español",
            )
            samples.forEach { text ->
                execute("insert into search_spike_documents (content) values ('${text.replace("'", "''")}')")
            }

            Then("한국어가 검색된다 (문장체 + 띄어쓰기 없는 문장 둘 다)") {
                val results = dsl.selectFrom(doc).where(doc.content.search("한국어")).fetch().map { it.content }
                results shouldHaveSize 2
                results shouldContain "한국어공부하실분구해요"
            }

            Then("일본어가 검색된다") {
                dsl.selectFrom(doc).where(doc.content.search("日本語")).fetch() shouldHaveSize 1
            }

            Then("중국어가 검색된다 (간체 + 번체)") {
                dsl.selectFrom(doc).where(doc.content.search("中文")).fetch() shouldHaveSize 2
            }

            Then("러시아어가 검색된다") {
                dsl.selectFrom(doc).where(doc.content.search("русский")).fetch() shouldHaveSize 1
            }

            Then("베트남어가 검색된다") {
                dsl.selectFrom(doc).where(doc.content.search("Việt")).fetch() shouldHaveSize 1
            }

            Then("독일어가 검색된다") {
                dsl.selectFrom(doc).where(doc.content.search("Deutsch")).fetch() shouldHaveSize 1
            }

            // FTS 대신 trgm 을 고른 핵심 이유. 회귀하면 안 된다.
            Then("띄어쓰기 없는 한국어 문장도 걸린다") {
                val results = dsl.selectFrom(doc).where(doc.content.search("한국어")).fetch().map { it.content }
                results shouldContain "한국어공부하실분구해요"
            }

            Then("부분 문자열이 걸린다 (apple 을 ppl 로)") {
                dsl.selectFrom(doc).where(doc.content.search("ppl")).fetch() shouldHaveSize 1
            }

            Then("발음부호 없이 검색해도 걸린다 (español 을 espanol 로)") {
                // 샘플 두 개("español, gracias" / "queso español") 모두 español 을 담고 있어 둘 다 걸린다.
                val results = dsl.selectFrom(doc).where(doc.content.search("espanol")).fetch().map { it.content }
                results shouldContain "me gusta comer queso español"
                results shouldHaveSize 2
            }
        }

        // 이스케이프가 없었다면 % 와 _ 가 와일드카드로 해석돼 "리터럴로는 안 맞아야 할" 행까지 오탐으로 걸린다.
        // 단순히 "1건 나온다"만 보면 이스케이프를 지워도 통과하므로, 오탐이 났을 데이터를 반드시 같이 넣는다.
        Given("검색어에 %, _ 가 있고 그 문자를 담은 행과 안 담은 행이 함께 있으면") {
            execute("insert into search_spike_documents (content) values ('할인 100% 진행중')")
            // % 를 와일드카드로 해석하면 "100" 만 있어도 걸린다 - 이 행이 그 오탐 대상이다.
            execute("insert into search_spike_documents (content) values ('할인 1000원 진행중')")
            execute("insert into search_spike_documents (content) values ('user_name 필드')")
            // _ 를 와일드카드(임의의 한 글자)로 해석하면 이 행도 걸린다 - 오탐 대상.
            execute("insert into search_spike_documents (content) values ('user1name 필드')")

            Then("%가 든 검색어(100%)는 % 를 담은 행만 리터럴로 매칭된다") {
                val results = dsl.selectFrom(doc).where(doc.content.search("100%")).fetch().map { it.content }
                results shouldHaveSize 1
                results shouldContain "할인 100% 진행중"
            }

            Then("_가 든 검색어(user_name)는 _ 를 담은 행만 리터럴로 매칭된다") {
                val results = dsl.selectFrom(doc).where(doc.content.search("user_name")).fetch().map { it.content }
                results shouldHaveSize 1
                results shouldContain "user_name 필드"
            }
        }
    }
}

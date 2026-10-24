package com.langlez.consumer

import com.langlez.consumer.item.Item
import com.langlez.consumer.item.ItemRepository
import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import com.querydsl.core.types.dsl.PathBuilder
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.data.repository.config.RepositoryConfigurationExtensionSupport
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.postgresql.PostgreSQLContainer

@SpringBootTest(
    classes = [ConsumerApp::class],
    properties = ["spring.jpa.hibernate.ddl-auto=validate"],
)
class RdbSupportTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repository: ItemRepository

    @Autowired
    lateinit var queryFactory: JPAQueryFactory

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16").also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    init {
        Given("classpath:migration 에 마이그레이션이 있고 앱 패키지 아래 아무 패키지에 리포지토리가 있을 때") {
            When("저장하고 조회하면") {
                Then("Flyway 가 스키마를 만들었고 리포지토리가 등록돼 있다") {
                    val saved = repository.save(Item("연필"))

                    saved.id.shouldNotBeNull()
                    repository.findById(saved.id!!).get().name shouldBe "연필"
                }
            }
        }

        Given("엔티티에 @CreatedDate 가 있을 때") {
            When("저장하면") {
                Then("JPA 감사가 켜져 있어 생성 시각이 채워진다") {
                    repository.saveAndFlush(Item("지우개")).createdAt.shouldNotBeNull()
                }
            }
        }

        Given("JPAQueryFactory 빈이 있을 때") {
            When("QueryDSL 로 조회하면") {
                Then("조건에 맞는 행만 돌려준다") {
                    repository.save(Item("자"))
                    val item = PathBuilder(Item::class.java, "item")

                    val names = queryFactory.select(item.getString("name")).from(item)
                        .where(item.getString("name").eq("자")).fetch()

                    names shouldBe listOf("자")
                }
            }
        }

        Given("share:rdb 에 의존하는 앱이 떴을 때") {
            Then("Spring Data 저장소 판별 로그는 WARN 부터만 남는다") {
                val context = LoggerFactory.getILoggerFactory() as LoggerContext

                context.getLogger(RepositoryConfigurationExtensionSupport::class.java).effectiveLevel shouldBe Level.WARN
            }
        }
    }
}

package com.langlez.notification.infrastructure

import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer

@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 브로커가 없는 테스트다. 리스너를 띄우면 접속 재시도 로그만 쌓인다.
        "spring.kafka.listener.auto-startup=false",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class NotificationRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: NotificationRepository

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

    init {
        fun notification(recipientId: Long, body: String) = Notification(
            recipientId = recipientId,
            type = "CHAT_MESSAGE",
            title = "notification.chat-message.title",
            body = body,
            data = """{"roomId":"7"}""",
        )

        Given("알림이 쌓여 있으면") {
            val first = repo.save(notification(1L, "첫번째"))
            val second = repo.save(notification(1L, "두번째"))
            val third = repo.save(notification(1L, "세번째"))
            repo.save(notification(2L, "남의 알림"))

            When("첫 페이지를 조회하면") {
                Then("최신순(id 내림차순)으로 내 알림만 나온다") {
                    val page = repo.findAll(recipientId = 1L, size = 2, cursor = null)

                    page shouldHaveSize 2
                    page[0].id shouldBe third.id
                    page[1].id shouldBe second.id
                }
            }

            When("커서로 다음 페이지를 조회하면") {
                Then("커서보다 오래된 것만 나온다") {
                    val page = repo.findAll(recipientId = 1L, size = 10, cursor = second.id)

                    page shouldHaveSize 1
                    page[0].id shouldBe first.id
                }
            }

            When("단건을 조회하면") {
                Then("읽음 상태와 데이터가 그대로 남아 있다") {
                    val found = repo.find(first.id)

                    found.shouldNotBeNull()
                    found.read shouldBe false
                    found.data shouldBe """{"roomId":"7"}"""
                }
            }

            When("읽음으로 바꿔 저장하면") {
                Then("다시 조회해도 읽음이다") {
                    repo.save(repo.find(third.id)!!.apply { read = true })

                    repo.find(third.id)!!.read shouldBe true
                }
            }
        }
    }
}

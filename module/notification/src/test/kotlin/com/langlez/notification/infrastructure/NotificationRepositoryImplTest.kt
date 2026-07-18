package com.langlez.notification.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.MySQLContainer

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class NotificationRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var notificationRepository: NotificationRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    companion object {
        @JvmField
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { mysql.jdbcUrl + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
        }
    }

    init {
        Given("NotificationRepository 가 주어졌을 때") {
            val user = memberRepository.save(
                Member(
                    email = "receiver@test.com",
                    username = "receiver",
                    nickname = "Receiver",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-receiver",
                    providerDisplayName = "Receiver"
                )
            )

            When("알림들을 저장하고 조회할 때") {
                val n1 = notificationRepository.save(
                    Notification(
                        recipientId = user.id,
                        type = "type.a",
                        title = "Title 1",
                        body = "Body 1"
                    )
                )
                val n2 = notificationRepository.save(
                    Notification(
                        recipientId = user.id,
                        type = "type.b",
                        title = "Title 2",
                        body = "Body 2"
                    )
                )
                val n3 = notificationRepository.save(
                    Notification(
                        recipientId = user.id,
                        type = "type.c",
                        title = "Title 3",
                        body = "Body 3"
                    )
                )

                Then("cursor 기반 페이지네이션이 역순(최신순)으로 정상 동작해야 한다") {
                    val page1 = notificationRepository.findByRecipient(user.id, null, 2)
                    page1 shouldHaveSize 2
                    page1[0].id shouldBe n3.id
                    page1[1].id shouldBe n2.id

                    val page2 = notificationRepository.findByRecipient(user.id, page1.last().id, 2)
                    page2 shouldHaveSize 1
                    page2[0].id shouldBe n1.id
                }

                Then("특정 알림을 읽음 처리(markAsRead)할 수 있어야 한다") {
                    notificationRepository.markAsRead(user.id, n1.id)
                    val readPage = notificationRepository.findByRecipient(user.id, null, 10)
                    val updatedN1 = readPage.find { it.id == n1.id }
                    updatedN1?.read shouldBe true
                    readPage.find { it.id == n2.id }?.read shouldBe false
                }

                Then("모든 알림을 읽음 처리(markAllAsRead)할 수 있어야 한다") {
                    notificationRepository.markAllAsRead(user.id)
                    val readPage = notificationRepository.findByRecipient(user.id, null, 10)
                    readPage.forEach {
                        it.read shouldBe true
                    }
                }
            }
        }
    }
}

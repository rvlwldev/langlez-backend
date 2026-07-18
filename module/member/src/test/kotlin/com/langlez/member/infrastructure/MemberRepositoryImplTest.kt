package com.langlez.member.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
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
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class MemberRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

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
        Given("MemberRepository 가 주어졌을 때") {
            
            When("회원을 저장하고 countAll 을 조회할 때") {
                val beforeCount = memberRepository.countAll()
                
                memberRepository.save(
                    Member(
                        email = "membertest@test.com",
                        username = "membertest",
                        nickname = "membertest",
                        provider = Member.Provider.GOOGLE,
                        providerId = "gp123",
                        providerDisplayName = "membertest"
                    )
                )

                Then("count 가 1 증가해야 한다") {
                    val afterCount = memberRepository.countAll()
                    afterCount shouldBe beforeCount + 1
                }
            }

            When("여러 회원을 저장하고 커서 페이지네이션으로 조회할 때") {
                val m1 = memberRepository.save(
                    Member(
                        email = "member1@test.com",
                        username = "member1",
                        nickname = "member1",
                        provider = Member.Provider.GOOGLE,
                        providerId = "gp1",
                        providerDisplayName = "member1"
                    )
                )
                val m2 = memberRepository.save(
                    Member(
                        email = "member2@test.com",
                        username = "member2",
                        nickname = "member2",
                        provider = Member.Provider.GOOGLE,
                        providerId = "gp2",
                        providerDisplayName = "member2"
                    )
                )

                Then("ID 내림차순으로 커서 페이징이 되어야 한다") {
                    val page1 = memberRepository.findAll(null, 1)
                    page1 shouldHaveSize 1
                    page1[0].id shouldBe m2.id

                    val page2 = memberRepository.findAll(m2.id, 1)
                    page2 shouldHaveSize 1
                    page2[0].id shouldBe m1.id
                }
            }
        }
    }
}

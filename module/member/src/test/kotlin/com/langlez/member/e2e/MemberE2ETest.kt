package com.langlez.member.e2e

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRole
import com.langlez.member.infrastructure.persistence.JpaMemberRepository
import io.kotest.core.spec.DisplayName
import io.kotest.core.spec.style.FunSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.restassured.RestAssured
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
@DisplayName("E2E: 회원 데이터 저장 및 조회 테스트")
class MemberE2ETest : FunSpec() {
    override fun extensions() = listOf(SpringExtension)

    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var memberRepository: JpaMemberRepository

    companion object {
        @Container
        val mysql = MySQLContainer<Nothing>("mysql:8.0").apply {
            withDatabaseName("langlez_test")
            withUsername("test")
            withPassword("test")
            start()
        }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)
        }
    }

    init {
        beforeSpec {
            RestAssured.port = port
        }

        beforeTest {
            memberRepository.deleteAll()
        }

        test("회원 저장 후 DB에서 조회가 가능하다") {
            // Given
            val member = Member(
                email = "e2e@test.com",
                nickname = "e2e_user",
                profileImageUrl = "http://e2e.com/img.png",
                provider = "google",
                providerId = "google_e2e_id"
            )

            // When
            memberRepository.save(member)

            // Then
            val found = memberRepository.findByEmail("e2e@test.com")
            found shouldNotBe null
            found?.nickname shouldBe "e2e_user"
            found?.role shouldBe MemberRole.MEMBER
        }
    }
}

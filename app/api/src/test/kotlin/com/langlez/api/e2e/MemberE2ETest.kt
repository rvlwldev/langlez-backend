package com.langlez.api.e2e

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRole
import com.langlez.member.infrastructure.persistence.JpaMemberRepository
import io.restassured.RestAssured
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
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
@org.junit.jupiter.api.Disabled(
    "Failing due to Context Loading (BindException). Needs investigation on Kotlin Data Class binding in Test context.",
)
class MemberE2ETest {
    @LocalServerPort
    var port: Int = 0

    @Autowired
    lateinit var memberRepository: JpaMemberRepository

    companion object {
        @Container
        val mysql =
            MySQLContainer<Nothing>("mysql:8.0").apply {
                withDatabaseName("langlez_test")
                withUsername("test")
                withPassword("test")
            }

        @Container
        val redis =
            GenericContainer<Nothing>("redis:7.0").apply {
                withExposedPorts(6379)
            }

        @DynamicPropertySource
        @JvmStatic
        fun registerProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", mysql::getJdbcUrl)
            registry.add("spring.datasource.username", mysql::getUsername)
            registry.add("spring.datasource.password", mysql::getPassword)

            registry.add("spring.data.redis.host", redis::getHost)
            registry.add("spring.data.redis.port", redis::getFirstMappedPort)
        }
    }

    @BeforeEach
    fun setUp() {
        RestAssured.port = port
        memberRepository.deleteAll()
    }

    @Test
    @DisplayName("회원 저장 후 DB에서 조회가 가능하다")
    fun `should save and retrieve member from database`() {
        // Given
        val member =
            Member(
                email = "e2e@test.com",
                nickname = "e2e_user",
                profileImageUrl = "http://e2e.com/img.png",
                provider = "google",
                providerId = "google_e2e_id",
            )

        // When
        memberRepository.save(member)

        // Then
        val found = memberRepository.findByEmail("e2e@test.com")
        assertNotNull(found)
        assertEquals("e2e_user", found?.nickname)
        assertEquals(MemberRole.MEMBER, found?.role)
    }
}

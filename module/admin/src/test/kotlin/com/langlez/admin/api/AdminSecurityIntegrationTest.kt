package com.langlez.admin.api

import com.langlez.admin.TestAdminApplication
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.echo.domain.CommentRepository
import com.langlez.echo.domain.PostRepository
import com.langlez.interest.application.InterestService
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.domain.MemberRepository
import com.langlez.report.domain.ReportRepository
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldNotBe
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.mock.web.MockHttpSession
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestBuilders.formLogin
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated
import org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    classes = [TestAdminApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "admin.username=admin",
        "admin.password=admin",
        "spring.main.allow-bean-definition-overriding=true",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
@AutoConfigureMockMvc
@Import(AdminSecurityIntegrationTest.MockConfig::class)
class AdminSecurityIntegrationTest : DescribeSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var mockMvc: MockMvc

    @TestConfiguration
    class MockConfig {
        @Bean
        fun memberRepository(): MemberRepository = mockk(relaxed = true)

        @Bean
        fun memberOnlineTracker(): MemberOnlineTracker = mockk(relaxed = true)

        @Bean
        fun chatRoomRepository(): ChatRoomRepository = mockk(relaxed = true)

        @Bean
        fun chatMessageRepository(): ChatMessageRepository = mockk(relaxed = true)

        @Bean
        fun attachmentRepository(): AttachmentRepository = mockk(relaxed = true)

        @Bean
        fun postRepository(): PostRepository = mockk(relaxed = true)

        @Bean
        fun commentRepository(): CommentRepository = mockk(relaxed = true)

        @Bean
        fun reportRepository(): ReportRepository = mockk(relaxed = true)

        @Bean
        fun interestService(): InterestService = mockk(relaxed = true)

        @Bean
        fun entityManagerFactory(): jakarta.persistence.EntityManagerFactory = mockk(relaxed = true)

        @Bean
        fun entityManager(): jakarta.persistence.EntityManager = mockk(relaxed = true)
    }

    init {
        describe("/admin/** Security 통합 테스트") {

            context("인증되지 않은 사용자가 대시보드(/admin/)에 접근할 때") {
                it("로그인 페이지(/admin/login)로 리다이렉트되어야 한다") {
                    mockMvc.perform(get("/admin/"))
                        .andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("http://localhost/admin/login"))
                }
            }

            context("잘못된 자격 증명으로 로그인할 때") {
                it("인증에 실패해야 한다") {
                    mockMvc.perform(
                        formLogin("/admin/login")
                            .user("admin")
                            .password("wrongpassword")
                    )
                        .andExpect(unauthenticated())
                        .andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/admin/login?error"))
                }
            }

            context("올바른 자격 증명으로 로그인할 때") {
                it("인증에 성공하고 대시보드로 이동할 수 있어야 한다") {
                    val result = mockMvc.perform(
                        formLogin("/admin/login")
                            .user("admin")
                            .password("admin")
                    )
                        .andExpect(authenticated().withUsername("admin").withRoles("ADMIN"))
                        .andExpect(status().is3xxRedirection)
                        .andExpect(redirectedUrl("/admin"))
                        .andReturn()

                    val session = result.request.getSession(false) as? MockHttpSession
                    session shouldNotBe null

                    // 리다이렉트된 대시보드가 실제로 200으로 렌더링되는지 확인
                    mockMvc.perform(get("/admin").session(session!!))
                        .andExpect(status().isOk)
                }
            }
        }
    }
}

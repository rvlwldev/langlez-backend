package com.langlez.member.application

import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.HttpStatus

class MemberSignInTest : BehaviorSpec({

    val service = mockk<MemberService>()
    val signIn = MemberSignIn(service)

    afterEach { clearMocks(service, answers = false) }

    fun member(id: Long = 1L, status: Member.Status = Member.Status.ACTIVE) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        status = status,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "user$id",
    )

    Given("소셜 로그인 진입 시") {
        When("providerId 가 빈 문자열이면") {
            Then("400 예외가 발생하고 회원 조회는 일어나지 않는다") {
                val ex = shouldThrow<LanglezException> {
                    signIn.authenticate("google", "  ", "a@test.com", "name")
                }
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "auth.invalid-request"

                verify(exactly = 0) { service.findByProvider(any(), any()) }
            }
        }

        When("알 수 없는 provider 문자열이면") {
            Then("400 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    signIn.authenticate("kakao", "k1", "a@test.com", "name")
                }
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "auth.invalid-request"
            }
        }

        When("이미 가입된 회원이 활성 상태면") {
            every { service.findByProvider(Member.Provider.GOOGLE, "g1") } returns member(1L)

            Then("그 회원 정보를 돌려준다") {
                val account = signIn.authenticate("google", "g1", "a@test.com", "name")

                account.id shouldBe 1L
                account.handle shouldBe "user1"
                account.role shouldBe "ROLE_MEMBER"
            }
        }

        When("이미 가입된 회원이 정지 상태면") {
            every { service.findByProvider(Member.Provider.GOOGLE, "g2") } returns
                member(2L, Member.Status.SUSPENDED)

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    signIn.authenticate("google", "g2", "a@test.com", "name")
                }
                ex.status shouldBe HttpStatus.FORBIDDEN
                ex.message shouldBe "member.suspended"
            }
        }

        When("이미 가입된 회원이 탈퇴 상태면") {
            every { service.findByProvider(Member.Provider.GOOGLE, "g3") } returns
                member(3L, Member.Status.WITHDRAWN)

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    signIn.authenticate("google", "g3", "a@test.com", "name")
                }
                ex.status shouldBe HttpStatus.FORBIDDEN
                ex.message shouldBe "member.withdrawn"
            }
        }

        When("신규 가입인데 이메일이 없으면") {
            every { service.findByProvider(Member.Provider.GOOGLE, "g4") } returns null

            Then("400 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    signIn.authenticate("google", "g4", null, "name")
                }
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "auth.invalid-request"
            }
        }

        When("신규 가입인데 이메일이 다른 계정에 이미 쓰였으면") {
            every { service.findByProvider(Member.Provider.GOOGLE, "g5") } returns null
            every { service.findByEmail("dup@test.com") } returns member(9L)

            Then("409 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    signIn.authenticate("google", "g5", "dup@test.com", "name")
                }
                ex.status shouldBe HttpStatus.CONFLICT
                ex.message shouldBe "auth.email-conflict"
            }
        }

        When("신규 가입이 정상이면") {
            every { service.findByProvider(Member.Provider.GOOGLE, "g6") } returns null
            every { service.findByEmail("new@test.com") } returns null
            every {
                service.createMember(Member.Provider.GOOGLE, "g6", "new@test.com", "New User")
            } returns member(10L)

            Then("회원을 만들고 그 정보를 돌려준다") {
                val account = signIn.authenticate("google", "g6", "new@test.com", "New User")

                account.id shouldBe 10L
                verify { service.createMember(Member.Provider.GOOGLE, "g6", "new@test.com", "New User") }
            }
        }
    }

    Given("리프레시용 로그인 가능 여부 조회 시") {
        When("회원이 없으면") {
            every { service.findById(404L) } returns null

            Then("null 을 반환한다") {
                signIn.findLoginable(404L).shouldBeNull()
            }
        }

        When("회원이 활성 상태면") {
            every { service.findById(1L) } returns member(1L)

            Then("회원 정보를 반환한다") {
                val account = signIn.findLoginable(1L)

                account?.id shouldBe 1L
                account?.role shouldBe "ROLE_MEMBER"
            }
        }

        When("회원이 정지 상태면") {
            every { service.findById(2L) } returns member(2L, Member.Status.SUSPENDED)

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { signIn.findLoginable(2L) }
                ex.status shouldBe HttpStatus.FORBIDDEN
                ex.message shouldBe "member.suspended"
            }
        }

        When("회원이 탈퇴 상태면") {
            every { service.findById(3L) } returns member(3L, Member.Status.WITHDRAWN)

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { signIn.findLoginable(3L) }
                ex.status shouldBe HttpStatus.FORBIDDEN
                ex.message shouldBe "member.withdrawn"
            }
        }
    }
})

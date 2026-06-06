package com.langlez.member.application

import com.langlez.core.OutBoxEventPublisher
import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.http.HttpStatus
import java.time.Instant
import java.time.temporal.ChronoUnit

class MemberServiceTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val publisher = mockk<OutBoxEventPublisher>(relaxed = true)
    val service = MemberService(repo, publisher)

    afterEach { clearMocks(repo, publisher, answers = false) }

    fun createMember(
        id: Long = 1L,
        username: String = "testuser",
        nickname: String = "Test User",
        lastUsernameUpdatedAt: Instant? = null,
        lastNicknameUpdatedAt: Instant? = null,
    ) = Member(
        id = id,
        email = "test@example.com",
        username = username,
        nickname = nickname,
        provider = MemberProvider("g123", MemberProvider.Type.GOOGLE, nickname),
        lastUsernameUpdatedAt = lastUsernameUpdatedAt,
        lastNicknameUpdatedAt = lastNicknameUpdatedAt,
    )

    Given("유저네임 변경 시") {
        When("유효한 유저네임으로 변경하면") {
            val member = createMember()
            every { repo.findById(1L) } returns member
            every { repo.findByUsername("newuser123") } returns null
            every { repo.save(any()) } answers { firstArg() }

            Then("유저네임이 변경되고 타임스탬프가 기록된다") {
                val result = service.updateUsername(1L, "newuser123")
                result.username shouldBe "newuser123"
                verify { repo.save(match { it.username == "newuser123" && it.lastUsernameUpdatedAt != null }) }
            }
        }

        When("유효하지 않은 유저네임으로 변경하면") {
            val member = createMember()
            every { repo.findById(1L) } returns member

            Then("BAD_REQUEST 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updateUsername(1L, "ab")
                }
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "member.username.invalid"
            }
        }

        When("이미 사용 중인 유저네임으로 변경하면") {
            val member = createMember()
            val other = createMember(id = 2L, username = "taken_user")
            every { repo.findById(1L) } returns member
            every { repo.findByUsername("taken_user") } returns other

            Then("CONFLICT 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updateUsername(1L, "taken_user")
                }
                ex.status shouldBe HttpStatus.CONFLICT
                ex.message shouldBe "member.username.duplicated"
            }
        }

        When("쿨다운 기간 내에 변경하면") {
            val member = createMember(lastUsernameUpdatedAt = Instant.now().minus(5, ChronoUnit.DAYS))
            every { repo.findById(1L) } returns member

            Then("BAD_REQUEST 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updateUsername(1L, "newuser123")
                }
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "member.username.cooldown"
            }
        }

        When("쿨다운 기간이 지난 후 변경하면") {
            val member = createMember(lastUsernameUpdatedAt = Instant.now().minus(16, ChronoUnit.DAYS))
            every { repo.findById(1L) } returns member
            every { repo.findByUsername("newuser123") } returns null
            every { repo.save(any()) } answers { firstArg() }

            Then("유저네임이 변경된다") {
                val result = service.updateUsername(1L, "newuser123")
                result.username shouldBe "newuser123"
            }
        }

        When("회원이 존재하지 않으면") {
            every { repo.findById(999L) } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.updateUsername(999L, "newuser123")
                }.status shouldBe HttpStatus.NOT_FOUND
            }
        }
    }

    Given("닉네임 변경 시") {
        When("정상적으로 변경하면") {
            val member = createMember()
            every { repo.findById(1L) } returns member
            every { repo.save(any()) } answers { firstArg() }

            Then("닉네임이 변경되고 타임스탬프가 기록된다") {
                val result = service.updateNickname(1L, "New Name")
                result.nickname shouldBe "New Name"
                verify { repo.save(match { it.nickname == "New Name" && it.lastNicknameUpdatedAt != null }) }
            }
        }

        When("쿨다운 기간 내에 변경하면") {
            val member = createMember(lastNicknameUpdatedAt = Instant.now().minus(5, ChronoUnit.DAYS))
            every { repo.findById(1L) } returns member

            Then("BAD_REQUEST 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updateNickname(1L, "New Name")
                }
                ex.status shouldBe HttpStatus.BAD_REQUEST
                ex.message shouldBe "member.nickname.cooldown"
            }
        }

        When("쿨다운 기간이 지난 후 변경하면") {
            val member = createMember(lastNicknameUpdatedAt = Instant.now().minus(16, ChronoUnit.DAYS))
            every { repo.findById(1L) } returns member
            every { repo.save(any()) } answers { firstArg() }

            Then("닉네임이 변경된다") {
                val result = service.updateNickname(1L, "New Name")
                result.nickname shouldBe "New Name"
            }
        }
    }
})

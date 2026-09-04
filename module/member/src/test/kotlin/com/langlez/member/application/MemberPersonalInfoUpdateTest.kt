package com.langlez.member.application

import com.langlez.member.contract.OnlineTracker
import com.langlez.attachment.contract.Storage
import com.langlez.exception.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

/**
 * 성별/생년월일/국가는 프로필이 아니라 계정 소유다.
 * `PATCH /api/v1/profiles/me` 에 있던 세 항목이 `PATCH /api/v1/members/me` 로 옮겨왔다.
 */
class MemberPersonalInfoUpdateTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val creator = mockk<MemberCreator>()
    val tracker = mockk<OnlineTracker>()
    val storage = mockk<Storage>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val tx = mockk<TransactionTemplate>()

    val service = MemberService(repo, creator, tracker, storage, publisher, tx)

    afterEach { clearMocks(repo, answers = false) }

    fun member(id: Long = 1L) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
    )

    Given("개인정보가 이미 채워진 회원이면") {
        val target = member().apply {
            gender = Member.Gender.FEMALE
            birthDay = LocalDate.of(1995, 3, 14)
            country = "KR"
        }

        When("성별만 보내면") {
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updatePersonalInfo(1L, Member.Gender.MALE, null, null, null)

            Then("성별만 바뀌고 나머지는 보존된다") {
                updated.gender shouldBe Member.Gender.MALE
                updated.birthDay shouldBe LocalDate.of(1995, 3, 14)
                updated.country shouldBe "KR"
            }
        }
    }

    Given("닉네임이 이미 있는 회원이면") {
        val target = member().apply { changeNickname("기존닉네임") }

        When("성별만 보내면") {
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updatePersonalInfo(1L, Member.Gender.MALE, null, null, null)

            Then("닉네임은 보존된다") {
                updated.nickname shouldBe "기존닉네임"
            }
        }

        When("새 닉네임을 보내면") {
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updatePersonalInfo(1L, null, null, null, "새닉네임")

            Then("닉네임이 바뀐다") {
                updated.nickname shouldBe "새닉네임"
            }
        }

        When("공백만 있는 닉네임을 보내면") {
            every { repo.find(1L) } returns target

            Then("400 LanglezException 이 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updatePersonalInfo(1L, null, null, null, "   ")
                }
                ex.status.value() shouldBe 400
                ex.message shouldBe "member.nickname.invalid"
            }
        }

        When("앞뒤 공백이 섞인 닉네임을 보내면") {
            every { repo.find(1L) } returns target
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updatePersonalInfo(1L, null, null, null, "  공백지수  ")

            Then("trim 되어 저장된다") {
                updated.nickname shouldBe "공백지수"
            }
        }

        When("최대 길이를 초과한 닉네임을 보내면") {
            every { repo.find(1L) } returns target

            Then("400 LanglezException 이 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.updatePersonalInfo(1L, null, null, null, "a".repeat(Member.NICKNAME_MAX_LENGTH + 1))
                }
                ex.status.value() shouldBe 400
                ex.message shouldBe "member.nickname.invalid"
            }
        }

        listOf(
            "한국어닉네임",
            "にほんごニックネーム",
            "中文昵称",
            "Кириллица",
            "Émile Zøe",
        ).forEach { nickname ->
            When("$nickname 처럼 다국어 문자를 보내면") {
                every { repo.find(1L) } returns target
                every { repo.save(any()) } answers { firstArg() }

                val updated = service.updatePersonalInfo(1L, null, null, null, nickname)

                Then("그대로 저장된다") {
                    updated.nickname shouldBe nickname
                }
            }
        }
    }

    Given("개인정보가 비어 있는 회원이면") {

        When("세 항목을 모두 보내면") {
            every { repo.find(1L) } returns member()
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updatePersonalInfo(1L, Member.Gender.FEMALE, LocalDate.of(2000, 1, 2), "US", null)

            Then("전부 반영되고 country 는 locale 로도 읽힌다") {
                updated.gender shouldBe Member.Gender.FEMALE
                updated.birthDay shouldBe LocalDate.of(2000, 1, 2)
                updated.locale?.country shouldBe "US"
            }
        }

        // null 을 "지움"으로 해석하면 부분 수정에서 안 보낸 필드가 통째로 날아간다.
        When("아무 항목도 보내지 않으면") {
            every { repo.find(1L) } returns member().apply { gender = Member.Gender.MALE; country = "JP" }
            every { repo.save(any()) } answers { firstArg() }

            val updated = service.updatePersonalInfo(1L, null, null, null, null)

            Then("기존 값이 그대로 남는다") {
                updated.gender shouldBe Member.Gender.MALE
                updated.country shouldBe "JP"
            }
        }
    }

    Given("없는 회원이면") {
        When("개인정보 수정을 시도하면") {
            every { repo.find(99L) } returns null

            Then("404 LanglezException 이 발생한다") {
                shouldThrow<LanglezException> {
                    service.updatePersonalInfo(99L, Member.Gender.MALE, null, null, null)
                }.status.value() shouldBe 404
            }
        }
    }
})

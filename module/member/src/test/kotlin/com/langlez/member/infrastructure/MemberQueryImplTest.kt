package com.langlez.member.infrastructure

import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.maps.shouldBeEmpty
import io.kotest.matchers.maps.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.LocalDate
import java.util.Locale

/**
 * profile 모듈이 계정 정보를 보는 유일한 통로다.
 * 여기가 깨지면 프로필 화면의 성별/국가/생년월일이 통째로 사라진다.
 */
class MemberQueryImplTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val query = MemberQueryImpl(repo)

    afterEach { clearMocks(repo, answers = false) }

    fun member(id: Long = 1L, handle: String = "user$id") = Member(
        id = id,
        email = "user$id@test.com",
        handle = handle,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
    )

    Given("handle 로 회원 id 를 물으면") {

        When("있는 handle 이면") {
            every { repo.find("alice") } returns member(7L, "alice")

            Then("회원 id 를 돌려준다") {
                query.findIdByHandle("alice") shouldBe 7L
            }
        }

        When("없는 handle 이면") {
            every { repo.find("ghost") } returns null

            Then("null 을 돌려준다") {
                query.findIdByHandle("ghost") shouldBe null
            }
        }
    }

    Given("프로필용 계정 정보를 물으면") {

        When("개인정보가 채워진 회원이면") {
            every { repo.find(1L) } returns member().apply {
                gender = Member.Gender.FEMALE
                birthDay = LocalDate.of(1995, 3, 14)
                country = "KR"
            }

            val info = query.findProfileInfo(1L)

            Then("성별은 enum 이 아니라 이름 문자열로 나온다") {
                info?.gender shouldBe "FEMALE"
            }

            Then("country 는 locale 로 변환돼 나온다") {
                info?.locale shouldBe Locale.of("", "KR")
            }

            Then("생년월일과 handle 이 함께 실린다") {
                info?.birthDay shouldBe LocalDate.of(1995, 3, 14)
                info?.handle shouldBe "user1"
            }
        }

        When("없는 회원이면") {
            every { repo.find(99L) } returns null

            Then("null 을 돌려준다") {
                query.findProfileInfo(99L) shouldBe null
            }
        }
    }

    Given("여러 회원의 계정 정보를 한 번에 물으면") {

        // 목록 화면에서 회원 수만큼 단건 조회가 나가면 N+1 이다. 왕복 한 번으로 끝나야 한다.
        When("id 가 중복돼 들어오면") {
            every { repo.findAll(setOf(1L, 2L)) } returns listOf(member(1L), member(2L))

            val infos = query.findProfileInfos(listOf(1L, 2L, 1L))

            Then("중복을 걷어내고 한 번만 조회한다") {
                infos shouldHaveSize 2
                infos[1L]?.handle shouldBe "user1"
                verify(exactly = 1) { repo.findAll(setOf(1L, 2L)) }
            }
        }

        When("빈 컬렉션이면") {
            Then("빈 IN () 쿼리를 만들지 않고 바로 빈 맵을 돌려준다") {
                query.findProfileInfos(emptyList()).shouldBeEmpty()
                verify(exactly = 0) { repo.findAll(any<Collection<Long>>()) }
            }
        }
    }
})

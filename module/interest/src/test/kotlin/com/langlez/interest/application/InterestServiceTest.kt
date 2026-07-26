package com.langlez.interest.application

import com.langlez.interest.domain.Interest
import com.langlez.interest.domain.InterestRepository
import com.langlez.interest.domain.MemberInterest
import com.langlez.interest.domain.MemberInterestRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Locale

class InterestServiceTest : BehaviorSpec({

    val interestRepo = mockk<InterestRepository>()
    val memberInterestRepo = mockk<MemberInterestRepository>()
    val service = InterestService(interestRepo, memberInterestRepo)

    Given("회원이 자기 언어로 관심사를 설정할 때") {
        val memberId = 1L
        val ko = Locale.forLanguageTag("ko")

        When("이미 존재하는 이름이면 새로 만들지 않는다") {
            val existing = Interest(ko = "등산")
            every { interestRepo.findByColumn("ko", "등산") } returns existing
            every { memberInterestRepo.findByMemberId(memberId) } returns emptyList()
            every { memberInterestRepo.saveAll(any()) } answers { firstArg() }
            every { memberInterestRepo.deleteAll(any()) } returns Unit

            service.setMemberInterests(memberId, ko, listOf("등산"))

            Then("새 Interest를 저장하지 않는다") {
                verify(exactly = 0) { interestRepo.save(any()) }
            }
        }

        When("없는 이름이면 새 Interest를 그 언어 컬럼만 채워 생성한다") {
            every { interestRepo.findByColumn("ko", "서핑") } returns null
            val created = Interest(ko = "서핑")
            every { interestRepo.save(match { it.ko == "서핑" && it.en == null }) } returns created
            every { memberInterestRepo.findByMemberId(memberId) } returns emptyList()
            every { memberInterestRepo.saveAll(any()) } answers { firstArg() }
            every { memberInterestRepo.deleteAll(any()) } returns Unit

            service.setMemberInterests(memberId, ko, listOf("서핑"))

            Then("ko 컬럼만 채워진 새 Interest가 저장된다") {
                verify { interestRepo.save(match { it.ko == "서핑" && it.en == null }) }
            }
        }
    }
})

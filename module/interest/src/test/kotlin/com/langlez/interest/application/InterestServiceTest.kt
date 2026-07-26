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

    Given("admin이 관심사를 병합할 때") {
        val from = Interest(ko = "하이킹")
        val to = Interest(ko = null, en = "Hiking")

        When("from에만 있는 언어값은 to로 백필되고, from은 삭제된다") {
            every { interestRepo.findById(7L) } returns from
            every { interestRepo.findById(5L) } returns to
            every { interestRepo.save(match { it === to }) } returns to
            every { memberInterestRepo.findByInterestId(7L) } returns listOf(MemberInterest(1L, 7L))
            every { memberInterestRepo.findByInterestId(5L) } returns emptyList()
            every { memberInterestRepo.saveAll(any()) } answers { firstArg() }
            every { interestRepo.delete(match { it === from }) } returns Unit

            service.merge(fromId = 7L, toId = 5L)

            Then("to.ko가 백필되고 from은 삭제된다") {
                to.ko shouldBe "하이킹"
                verify { interestRepo.delete(match { it === from }) }
            }
        }

        When("같은 회원이 from/to를 둘 다 가지고 있으면 중복 생성하지 않는다") {
            every { interestRepo.findById(7L) } returns from
            every { interestRepo.findById(5L) } returns to
            every { interestRepo.save(match { it === to }) } returns to
            every { memberInterestRepo.findByInterestId(7L) } returns listOf(MemberInterest(1L, 7L))
            every { memberInterestRepo.findByInterestId(5L) } returns listOf(MemberInterest(1L, 5L))
            val saved = slot<List<MemberInterest>>()
            every { memberInterestRepo.saveAll(capture(saved)) } answers { firstArg() }
            every { interestRepo.delete(any()) } returns Unit

            service.merge(fromId = 7L, toId = 5L)

            Then("추가로 저장되는 MemberInterest가 없다") {
                saved.captured shouldBe emptyList()
            }
        }
    }
})

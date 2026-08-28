package com.langlez.member.api

import com.langlez.core.Storage
import com.langlez.member.api.request.MemberUpdateImageRequest
import com.langlez.member.api.request.MemberUpdatePersonalInfoRequest
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class MemberControllerTest : BehaviorSpec({

    val service = mockk<MemberService>()
    val repo = mockk<MemberRepository>()
    val controller = MemberController(service, repo)

    afterEach { clearMocks(service, repo, answers = false) }

    fun member(id: Long = 1L) = Member(
        id = id,
        email = "user$id@test.com",
        handle = "user$id",
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
    )

    Given("프로필 이미지 업로드 URL 발급 시") {
        When("filename을 전달하면") {
            val result = Storage.PresignedResult(key = "member/2026-08-03/uuid_photo.jpg", presigned = "https://presigned.url")
            every { service.presignProfileUrl(1L, "photo.jpg") } returns result

            val response = controller.getImageUploadUrl(1L, "photo.jpg")

            Then("Storage.PresignedResult를 그대로 반환한다") {
                response shouldBe result
            }
        }
    }

    Given("프로필 이미지 확정 시") {
        When("key를 전달하면") {
            val updated = member().apply { imageUrl = "https://cdn.langlez.com/key123" }
            every { service.updateProfileUrl(1L, "key123") } returns updated

            val response = controller.patchImage(1L, MemberUpdateImageRequest("key123"))

            Then("갱신된 imageUrl이 포함된 MemberMeResponse를 반환한다") {
                response.imageUrl shouldBe "https://cdn.langlez.com/key123"
            }
        }
    }

    Given("개인정보 수정 시") {
        When("성별과 국가만 보내면") {
            val updated = member().apply { gender = Member.Gender.MALE; country = "US" }
            every { service.updatePersonalInfo(1L, Member.Gender.MALE, null, "US") } returns updated

            val response = controller.patchPersonalInfo(
                1L,
                MemberUpdatePersonalInfoRequest(gender = Member.Gender.MALE, country = "US"),
            )

            Then("보내지 않은 birthDay 는 null 로 넘어가고 응답에 갱신된 값이 실린다") {
                response.gender shouldBe "MALE"
                response.country shouldBe "US"
                verify { service.updatePersonalInfo(1L, Member.Gender.MALE, null, "US") }
            }
        }
    }

    Given("회원 탈퇴 요청 시") {
        When("탈퇴를 요청하면") {
            every { service.withdrawMember(1L) } returns Unit

            controller.withdraw(1L)

            Then("MemberService.withdrawMember가 호출된다") {
                verify { service.withdrawMember(1L) }
            }
        }
    }
})

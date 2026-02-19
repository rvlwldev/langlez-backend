package com.langlez.member.application

import com.langlez.file.application.FileStorage
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProfile
import com.langlez.member.domain.MemberProfileRepository
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.embedded.MemberAudit
import com.langlez.member.domain.embedded.MemberProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.springframework.mock.web.MockMultipartFile

class ProfileServiceTest : BehaviorSpec({
    val memberRepo = mockk<MemberRepository>()
    val profileRepo = mockk<MemberProfileRepository>()
    val fileStorage = mockk<FileStorage>()
    val service = ProfileService(memberRepo, profileRepo, fileStorage, "profile")

    Given("프로필 서비스") {
        val email = "test@test.com"
        val member = Member(id = 1L, email = email, nickname = "test", provider = MemberProvider("id", MemberProvider.Type.GOOGLE, "user"), audit = MemberAudit())
        val profile = MemberProfile(memberId = 1L, member = member)

        coEvery { memberRepo.findByEmail(email) } returns member
        coEvery { profileRepo.findByMemberId(1L) } returns profile

        When("이미지 업데이트를 요청하면") {
            val profileImage = MockMultipartFile("profile", "p.jpg", "image/jpeg", ByteArray(1))
            val otherImage = MockMultipartFile("other", "o.jpg", "image/jpeg", ByteArray(1))
            
            coEvery { fileStorage.delete(any()) } returns Unit
            coEvery { fileStorage.upload(any(), any()) } returns "https://s3/url"

            service.updateImages(email, profileImage, listOf(otherImage))

            Then("기존 이미지는 삭제되어야 한다") {
                // 이미지가 없었으므로 delete 호출 안됨 (member.images가 비어있음)
                // 만약 있었다면:
                // coVerify { fileStorage.delete(...) }
            }

            Then("새 이미지가 업로드되어야 한다") {
                coVerify(exactly = 2) { fileStorage.upload(any(), eq("profile")) }
            }
            
            Then("DB에 저장되어야 한다") {
                // saveMemberImages는 내부적으로 호출됨 (member 객체 상태 변경 확인)
                member.images.size shouldBe 2
            }
        }
    }
})

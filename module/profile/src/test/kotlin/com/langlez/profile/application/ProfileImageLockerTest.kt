package com.langlez.profile.application

import com.langlez.core.LanglezException
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import org.springframework.http.HttpStatus

class ProfileImageLockerTest : BehaviorSpec({

    val repo = mockk<ProfileRepository>()
    val locker = ProfileImageLocker(repo)

    afterEach { clearMocks(repo, answers = false) }

    fun image(memberId: Long, url: String, sequence: Long = 1) =
        ProfileImage(memberId, url, sequence, 0L, false)

    Given("ProfileImageLocker 추가 사진 업로드 확정 시") {

        When("사진이 5장 미만일 때 추가 사진을 확정하면") {
            val addedImage = image(1L, "https://cdn/profiles/add.jpg", sequence = 3)
            every { repo.countImages(1L) } returns 2L
            every { repo.saveImage(any()) } returns addedImage

            val result = locker.confirmAdditionalImage(1L, "https://cdn/profiles/add.jpg")

            Then("추가 사진으로 저장된다") {
                result.represent shouldBe false
                result.url shouldBe "https://cdn/profiles/add.jpg"
                verify { repo.saveImage(match { it.sequence == 3L }) }
            }
        }

        When("이미 사진이 6장(최대)일 때 추가하면") {
            every { repo.countImages(1L) } returns 6L

            Then("BAD_REQUEST 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    locker.confirmAdditionalImage(1L, "https://cdn/profiles/over.jpg")
                }.status shouldBe HttpStatus.BAD_REQUEST.value()
            }
        }

        When("사진이 정확히 5장일 때 추가하면 (경계값)") {
            val addedImage = image(1L, "https://cdn/profiles/sixth.jpg", sequence = 6)
            every { repo.countImages(1L) } returns 5L
            every { repo.saveImage(any()) } returns addedImage

            Then("6번째 사진으로 저장된다") {
                val result = locker.confirmAdditionalImage(1L, "https://cdn/profiles/sixth.jpg")
                result.sequence shouldBe 6L
                verify { repo.saveImage(match { it.sequence == 6L }) }
            }
        }
    }
})

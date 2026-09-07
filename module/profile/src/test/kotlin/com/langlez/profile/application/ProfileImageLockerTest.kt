package com.langlez.profile.application

import com.langlez.exception.LanglezException
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
                }.status.value() shouldBe HttpStatus.BAD_REQUEST.value()
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

    // confirmRepresentImage 는 예전에 ProfileService.replaceRepresentImage 로 락을 통째로 우회했다.
    // 대표 사진도 새 행을 추가하는 것이라, 여기 없으면 동시성 없이 순차 호출만으로도 정원을 넘길 수 있었다 (C-11 (c)).
    Given("ProfileImageLocker 대표 사진 확정 시") {

        When("사진이 5장 미만일 때 대표 사진을 확정하면") {
            val addedImage = image(1L, "https://cdn/profiles/represent.jpg", sequence = 3).apply { represent = true }
            every { repo.findRepresentImage(1L) } returns null
            every { repo.countImages(1L) } returns 2L
            every { repo.saveImage(any()) } returns addedImage

            val result = locker.confirmRepresentImage(1L, "https://cdn/profiles/represent.jpg")

            Then("대표 사진으로 저장된다") {
                result.represent shouldBe true
                verify { repo.saveImage(match { it.sequence == 3L && it.represent }) }
            }
        }

        When("기존 대표 사진이 있을 때 확정하면") {
            val old = image(1L, "https://cdn/profiles/old.jpg", sequence = 1).apply { represent = true }
            val newImage = image(1L, "https://cdn/profiles/new.jpg", sequence = 3).apply { represent = true }
            every { repo.findRepresentImage(1L) } returns old
            every { repo.countImages(1L) } returns 2L
            every { repo.saveImage(match { !it.represent }) } returns old
            every { repo.saveImage(match { it.represent }) } returns newImage

            locker.confirmRepresentImage(1L, "https://cdn/profiles/new.jpg")

            Then("기존 대표 사진은 내려가고 새 사진이 대표로 저장된다") {
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/old.jpg" && !it.represent }) }
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/new.jpg" && it.represent }) }
            }
        }

        When("이미 사진이 6장(최대)일 때 대표 사진으로 확정하면 (동시성 없는 순차 호출)") {
            every { repo.findRepresentImage(1L) } returns null
            every { repo.countImages(1L) } returns 6L

            Then("BAD_REQUEST 예외가 발생해 정원을 넘기지 못한다") {
                shouldThrow<LanglezException> {
                    locker.confirmRepresentImage(1L, "https://cdn/profiles/over.jpg")
                }.status.value() shouldBe HttpStatus.BAD_REQUEST.value()
                verify(exactly = 0) { repo.saveImage(match { it.url == "https://cdn/profiles/over.jpg" }) }
            }
        }
    }
})

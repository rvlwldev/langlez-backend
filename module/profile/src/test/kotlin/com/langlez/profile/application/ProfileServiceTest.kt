package com.langlez.profile.application

import com.langlez.core.FileStorage
import com.langlez.core.LanglezException
import com.langlez.interest.application.InterestService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.http.HttpStatus

class ProfileServiceTest : BehaviorSpec({

    val repo = mockk<ProfileRepository>()
    val storage = mockk<FileStorage>()
    val profileImageLocker = mockk<ProfileImageLocker>()
    val interestService = mockk<InterestService>()

    val service = ProfileService(repo, storage, profileImageLocker, interestService)

    afterEach { clearMocks(repo, storage, profileImageLocker, interestService, answers = false) }

    fun member(id: Long) = Member(
        id = id,
        email = "user$id@test.com",
        username = "user$id",
        nickname = "User$id",
        provider = MemberProvider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = "User$id"
    )

    fun profile(id: Long) = Profile(id = id, member = member(id))

    fun image(memberId: Long, url: String, represent: Boolean = false, sequence: Long = 1) =
        ProfileImage(memberId, url, sequence, 0L, represent)

    Given("프로필 이미지 업로드 URL 발급 시") {

        When("image/jpeg contentType으로 요청하면") {
            every { storage.generateUploadUrl("photo.jpg", "image/jpeg", "profiles") } returns "https://presigned.url"

            val url = service.generateImageUploadUrl("photo.jpg", "image/jpeg")

            Then("Presigned URL을 반환한다") {
                url shouldBe "https://presigned.url"
                verify { storage.generateUploadUrl("photo.jpg", "image/jpeg", "profiles") }
            }
        }

        When("image/png contentType으로 요청하면") {
            every { storage.generateUploadUrl("photo.png", "image/png", "profiles") } returns "https://presigned.url/png"

            Then("정상적으로 URL을 반환한다") {
                service.generateImageUploadUrl("photo.png", "image/png") shouldContain "presigned"
            }
        }

        When("image/* 가 아닌 contentType(video/mp4)으로 요청하면") {
            Then("BAD_REQUEST 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.generateImageUploadUrl("video.mp4", "video/mp4")
                }.status shouldBe HttpStatus.BAD_REQUEST.value()
            }
        }

        When("application/pdf contentType으로 요청하면") {
            Then("BAD_REQUEST 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.generateImageUploadUrl("doc.pdf", "application/pdf")
                }.status shouldBe HttpStatus.BAD_REQUEST.value()
            }
        }
    }

    Given("대표 사진 업로드 확정 시") {

        When("기존 대표 사진이 없을 때 새 URL로 확정하면") {
            val newImage = image(1L, "https://cdn/profiles/new.jpg", represent = true)
            every { repo.findRepresentImage(1L) } returns null
            every { repo.countImages(1L) } returns 0L
            every { repo.saveImage(any()) } returns newImage

            val result = service.confirmRepresentImage(1L, "https://cdn/profiles/new.jpg")

            Then("대표 사진으로 저장된다") {
                result.represent shouldBe true
                result.url shouldBe "https://cdn/profiles/new.jpg"
            }
        }

        When("기존 대표 사진이 있을 때 새 URL로 확정하면") {
            val oldRepresent = image(1L, "https://cdn/profiles/old.jpg", represent = true)
            val newImage = image(1L, "https://cdn/profiles/new.jpg", represent = true, sequence = 2)
            every { repo.findRepresentImage(1L) } returns oldRepresent
            every { repo.countImages(1L) } returns 1L
            every { repo.saveImage(match { !it.represent }) } returns oldRepresent.apply { represent = false }
            every { repo.saveImage(match { it.represent }) } returns newImage

            service.confirmRepresentImage(1L, "https://cdn/profiles/new.jpg")

            Then("기존 대표 사진의 represent가 false로 변경되고 새 대표 사진이 저장된다") {
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/old.jpg" && !it.represent }) }
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/new.jpg" && it.represent }) }
            }
        }
    }

    Given("추가 사진 업로드 확정 시") {

        When("confirmAdditionalImage를 호출하면") {
            val addedImage = image(1L, "https://cdn/profiles/add.jpg", represent = false, sequence = 3)
            every { profileImageLocker.confirmAdditionalImage(1L, "https://cdn/profiles/add.jpg") } returns addedImage

            val result = service.confirmAdditionalImage(1L, "https://cdn/profiles/add.jpg")

            Then("ProfileImageLocker로 위임되어 수행된다") {
                result.represent shouldBe false
                result.url shouldBe "https://cdn/profiles/add.jpg"
                verify { profileImageLocker.confirmAdditionalImage(1L, "https://cdn/profiles/add.jpg") }
            }
        }
    }

    Given("대표 사진 변경 시") {

        When("등록된 사진 URL로 대표 변경 요청하면") {
            val target = image(1L, "https://cdn/profiles/existing.jpg", represent = false, sequence = 2)
            val oldRepresent = image(1L, "https://cdn/profiles/current.jpg", represent = true, sequence = 1)
            val newRepresent = image(1L, "https://cdn/profiles/existing.jpg", represent = true, sequence = 3)

            every { repo.findImageByUrl(1L, "https://cdn/profiles/existing.jpg") } returns target
            every { repo.findRepresentImage(1L) } returns oldRepresent
            every { repo.countImages(1L) } returns 2L
            every { repo.saveImage(match { !it.represent }) } returns oldRepresent.apply { represent = false }
            every { repo.saveImage(match { it.represent }) } returns newRepresent

            service.changeRepresentImage(1L, "https://cdn/profiles/existing.jpg")

            Then("기존 대표 사진이 해제되고 요청 사진이 대표가 된다") {
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/current.jpg" && !it.represent }) }
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/existing.jpg" && it.represent }) }
            }
        }

        When("존재하지 않는 사진 URL로 대표 변경 요청하면") {
            every { repo.findImageByUrl(1L, "https://cdn/profiles/ghost.jpg") } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.changeRepresentImage(1L, "https://cdn/profiles/ghost.jpg")
                }.status shouldBe HttpStatus.NOT_FOUND.value()
            }
        }
    }

    Given("프로필 조회 시") {

        When("존재하는 username으로 조회하면") {
            val p = profile(1L)
            every { repo.findProfileByUsername("user1") } returns p

            val result = service.getProfile("user1")

            Then("프로필 엔티티를 반환한다") {
                result shouldBe p
            }
        }

        When("존재하지 않는 username으로 조회하면") {
            every { repo.findProfileByUsername("ghost") } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.getProfile("ghost")
                }.status shouldBe HttpStatus.NOT_FOUND.value()
            }
        }
    }

    Given("방문자 수 조회 시") {

        When("Redis에 저장된 delta가 있으면") {
            every { repo.getVisitCountDelta("user1") } returns 7L

            Then("delta 값을 반환한다") {
                service.getVisitCount("user1") shouldBe 7L
            }
        }
    }

    Given("방문자 수 증가 시") {

        When("visitorId와 username으로 increaseVisitCount를 호출하면") {
            every { repo.increaseVisitCount(2L, "user1") } just runs

            service.increaseVisitCount(2L, "user1")

            Then("repo.increaseVisitCount가 호출된다") {
                verify { repo.increaseVisitCount(2L, "user1") }
            }
        }
    }
})

package com.langlez.profile.application

import com.langlez.core.Storage
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * 이미지 확정은 클라이언트가 준 URL 을 믿으면 안 된다.
 * key 로 받아 Storage 에 실제 업로드됐는지 확인(attach)하고, 스토리지가 돌려준 조회용 URL 만 저장한다.
 */
class ProfileImageConfirmTest : BehaviorSpec({

    val repo = mockk<ProfileRepository>()
    val storage = mockk<Storage>()
    val locker = mockk<ProfileImageLocker>()
    val follows = mockk<com.langlez.core.FollowQuery>(relaxed = true)
    val members = mockk<com.langlez.core.MemberQuery>(relaxed = true)
    val service = ProfileService(repo, storage, locker, follows, members)

    Given("업로드 URL 을 발급하면") {
        every {
            storage.presign(1L, "profiles", Storage.Type.IMAGE, "photo.jpg")
        } returns Storage.PresignedResult(key = "profiles/2026/uuid_photo.jpg", presigned = "https://s3/put?sig=abc")

        val result = service.generateImageUploadUrl(1L, "photo.jpg", "image/jpeg")

        Then("확정에 쓸 key 도 함께 내려준다") {
            // key 를 안 주면 클라이언트는 서명이 붙은 PUT URL 을 되돌려줄 수밖에 없다
            result.key shouldBe "profiles/2026/uuid_photo.jpg"
            result.presigned shouldBe "https://s3/put?sig=abc"
        }
    }

    Given("대표 이미지를 key 로 확정하면") {
        every { storage.attach("profiles/2026/uuid_photo.jpg", 1L) } returns "https://cdn/profiles/2026/uuid_photo.jpg"
        every { repo.findRepresentImage(1L) } returns null
        every { repo.countImages(1L) } returns 0L
        every { repo.saveImage(any()) } answers { firstArg() }

        val saved = service.confirmRepresentImage(1L, "profiles/2026/uuid_photo.jpg")

        Then("스토리지가 확인해 돌려준 URL 만 저장된다") {
            saved.url shouldBe "https://cdn/profiles/2026/uuid_photo.jpg"
            verify { storage.attach("profiles/2026/uuid_photo.jpg", 1L) }
            verify { repo.saveImage(match<ProfileImage> { it.url == "https://cdn/profiles/2026/uuid_photo.jpg" }) }
        }
    }

    Given("추가 이미지를 key 로 확정하면") {
        every { storage.attach("profiles/2026/extra.jpg", 1L) } returns "https://cdn/profiles/2026/extra.jpg"
        every {
            locker.confirmAdditionalImage(1L, "https://cdn/profiles/2026/extra.jpg")
        } returns ProfileImage(1L, "https://cdn/profiles/2026/extra.jpg", 2L, 0L, false)

        service.confirmAdditionalImage(1L, "profiles/2026/extra.jpg")

        Then("locker 에는 검증된 URL 이 전달된다") {
            verify { locker.confirmAdditionalImage(1L, "https://cdn/profiles/2026/extra.jpg") }
        }
    }
})

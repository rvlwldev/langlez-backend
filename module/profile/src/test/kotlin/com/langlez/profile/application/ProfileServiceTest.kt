package com.langlez.profile.application

import com.langlez.core.FollowQuery
import com.langlez.core.MemberQuery
import com.langlez.core.Storage
import com.langlez.exception.LanglezException
import com.langlez.profile.api.ProfileRequest
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import com.langlez.profile.domain.ProfileRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import java.util.Locale
import org.springframework.http.HttpStatus

class ProfileServiceTest : BehaviorSpec({

    val repo = mockk<ProfileRepository>()
    val storage = mockk<Storage>()
    val profileImageLocker = mockk<ProfileImageLocker>()

    val follows = mockk<FollowQuery>()
    val members = mockk<MemberQuery>()

    val service = ProfileService(repo, storage, profileImageLocker, follows, members)

    afterEach { clearMocks(repo, storage, profileImageLocker, follows, members, answers = false) }

    fun memberInfo(id: Long, handle: String = "user$id") = MemberQuery.ProfileInfo(
        id = id,
        handle = handle,
        gender = "SECRET",
        locale = null,
        birthDay = null,
    )

    fun profile(id: Long) = Profile(id = id)

    fun image(memberId: Long, url: String, represent: Boolean = false, sequence: Long = 1) =
        ProfileImage(memberId, url, sequence, 0L, represent)

    Given("남의 프로필 상세를 열면") {

        // 프로필 화면이 팔로워/팔로잉 숫자를 함께 그린다. 여기 안 실으면 클라이언트가 요청을 한 번 더 쏜다.
        When("팔로워와 팔로잉이 있는 회원이면") {
            Then("두 숫자가 응답에 실려 나간다") {
                every { repo.findProfileByUsername("target") } returns profile(9L)
                every { members.findProfileInfo(9L) } returns memberInfo(9L, "target")
                every { repo.increaseVisitCount(1L, "target") } returns Unit
                every { repo.getVisitCountDelta("target") } returns 2L
                every { follows.counts(9L) } returns FollowQuery.Counts(followers = 12L, followings = 3L)

                val detail = service.getProfileDetail(1L, "target", Locale.KOREA)

                detail.followerCount shouldBe 12L
                detail.followingCount shouldBe 3L
            }
        }
    }

    Given("프로필 이미지 업로드 URL 발급 시") {

        When("image/jpeg contentType으로 요청하면") {
            every { storage.presign(1L, "profiles", com.langlez.core.Storage.Type.IMAGE, "photo.jpg") } returns
                com.langlez.core.Storage.PresignedResult(key = "profiles/photo.jpg", presigned = "https://presigned.url")

            val url = service.generateImageUploadUrl(1L, "photo.jpg", "image/jpeg").presigned

            Then("Presigned URL을 반환한다") {
                url shouldBe "https://presigned.url"
                verify { storage.presign(1L, "profiles", com.langlez.core.Storage.Type.IMAGE, "photo.jpg") }
            }
        }

        When("image/png contentType으로 요청하면") {
            every { storage.presign(1L, "profiles", com.langlez.core.Storage.Type.IMAGE, "photo.png") } returns
                com.langlez.core.Storage.PresignedResult(key = "profiles/photo.png", presigned = "https://presigned.url/png")

            Then("정상적으로 URL을 반환한다") {
                service.generateImageUploadUrl(1L, "photo.png", "image/png").presigned shouldContain "presigned"
            }
        }

        When("image/* 가 아닌 contentType(video/mp4)으로 요청하면") {
            Then("BAD_REQUEST 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.generateImageUploadUrl(1L, "video.mp4", "video/mp4")
                }.status.value() shouldBe HttpStatus.BAD_REQUEST.value()
            }
        }

        When("application/pdf contentType으로 요청하면") {
            Then("BAD_REQUEST 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.generateImageUploadUrl(1L, "doc.pdf", "application/pdf")
                }.status.value() shouldBe HttpStatus.BAD_REQUEST.value()
            }
        }
    }

    Given("대표 사진 업로드 확정 시") {
        every { storage.attach("profiles/new.jpg", 1L) } returns "https://cdn/profiles/new.jpg"

        When("기존 대표 사진이 없을 때 새 URL로 확정하면") {
            val newImage = image(1L, "https://cdn/profiles/new.jpg", represent = true)
            every { repo.findRepresentImage(1L) } returns null
            every { repo.countImages(1L) } returns 0L
            every { repo.saveImage(any()) } returns newImage

            val result = service.confirmRepresentImage(1L, "profiles/new.jpg")

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

            service.confirmRepresentImage(1L, "profiles/new.jpg")

            Then("기존 대표 사진의 represent가 false로 변경되고 새 대표 사진이 저장된다") {
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/old.jpg" && !it.represent }) }
                verify { repo.saveImage(match { it.url == "https://cdn/profiles/new.jpg" && it.represent }) }
            }
        }
    }

    Given("추가 사진 업로드 확정 시") {
        every { storage.attach("profiles/add.jpg", 1L) } returns "https://cdn/profiles/add.jpg"

        When("confirmAdditionalImage를 호출하면") {
            val addedImage = image(1L, "https://cdn/profiles/add.jpg", represent = false, sequence = 3)
            every { profileImageLocker.confirmAdditionalImage(1L, "https://cdn/profiles/add.jpg") } returns addedImage

            val result = service.confirmAdditionalImage(1L, "profiles/add.jpg")

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
                }.status.value() shouldBe HttpStatus.NOT_FOUND.value()
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
                }.status.value() shouldBe HttpStatus.NOT_FOUND.value()
            }
        }
    }

    Given("내 프로필 수정 시") {

        // 성별/국가/생년월일은 PATCH /api/v1/members/me 로 옮겼다. 요청 DTO 에 아예 없어서 컴파일로 드러난다.
        When("자기소개 항목만 보내면") {
            val target = profile(1L).apply { bio = "old"; goal = "old goal" }
            every { repo.findProfile(1L) } returns target
            every { members.findProfileInfo(1L) } returns memberInfo(1L)
            every { repo.saveProfile(any()) } answers { firstArg() }

            val result = service.updateProfile(
                1L,
                ProfileRequest.Update(bio = "new bio", mbti = Profile.MBTI.INTJ),
                Locale.KOREA,
            )

            Then("보낸 항목만 바뀌고 안 보낸 goal 은 보존된다") {
                result.bio shouldBe "new bio"
                result.mbti shouldBe "INTJ"
                target.goal shouldBe "old goal"
            }

            Then("성별/국가/생년월일은 계정에서 읽어 응답에 실린다") {
                result.gender shouldBe "SECRET"
                result.locale shouldBe null
                result.birthDay shouldBe null
            }
        }

        When("프로필 행이 없으면") {
            every { repo.findProfile(2L) } returns null

            Then("NOT_FOUND 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.updateProfile(2L, ProfileRequest.Update(bio = "x"), Locale.KOREA)
                }.status.value() shouldBe HttpStatus.NOT_FOUND.value()
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

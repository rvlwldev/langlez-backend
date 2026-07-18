package com.langlez.profile.api

import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.profile.application.ProfileService
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class ProfileControllerTest : BehaviorSpec({

    val service = mockk<ProfileService>()
    val controller = ProfileController(service)

    afterEach { clearMocks(service, answers = false) }

    fun createMember(id: Long = 1L, username: String = "testuser", nickname: String = "Test User") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = nickname,
        provider = Member.Provider.GOOGLE,
        providerId = "g$id",
        providerDisplayName = nickname
    )

    Given("프로필 상세 조회 시") {
        When("존재하는 username을 조회하면") {
            val detail = ProfileResponse.Detail(
                username = "target",
                nickname = "Target User",
                bio = "hello",
                goal = "goal",
                want = "want",
                gender = "MALE",
                mbti = "INTJ",
                locale = null,
                birthDay = null,
                visitCount = 5L,
            )
            every { service.getProfileDetail(1L, "target") } returns detail

            Then("서비스 결과가 그대로 반환된다") {
                val result = controller.getProfile(1L, "target")
                result.username shouldBe "target"
                result.nickname shouldBe "Target User"
                result.visitCount shouldBe 5L
            }
        }

        When("존재하지 않는 username을 조회하면") {
            every { service.getProfileDetail(1L, "ghost") } throws LanglezException(404, "profile.not-found")

            Then("NOT_FOUND 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getProfile(1L, "ghost")
                }
                ex.status shouldBe 404
            }
        }
    }

    Given("내 프로필 수정 시") {
        When("서비스가 정상 처리하면") {
            val member = createMember()
            val profile = Profile(id = 1L, member = member, bio = "new bio", gender = Profile.Gender.MALE)
            val request = ProfileRequest.Update(bio = "new bio")
            every { service.updateProfile(1L, request) } returns profile

            Then("변경된 프로필이 반환된다") {
                val result = controller.updateProfile(1L, request)
                result.bio shouldBe "new bio"
                result.gender shouldBe "MALE"
            }
        }
    }

    Given("이미지 업로드 URL 발급 시") {
        When("정상적인 contentType으로 요청하면") {
            every { service.generateImageUploadUrl("photo.jpg", "image/jpeg") } returns "https://cdn/upload/photo.jpg"

            Then("업로드 URL이 반환된다") {
                val result = controller.getImageUploadUrl(1L, "photo.jpg", "image/jpeg")
                result["uploadUrl"] shouldBe "https://cdn/upload/photo.jpg"
            }
        }

        When("이미지가 아닌 contentType으로 요청하면") {
            every { service.generateImageUploadUrl("video.mp4", "video/mp4") } throws LanglezException(400, "file.unsupported-content-type")

            Then("BAD_REQUEST 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getImageUploadUrl(1L, "video.mp4", "video/mp4")
                }
                ex.status shouldBe 400
            }
        }
    }

    Given("대표 사진 등록 시") {
        When("서비스가 정상 처리하면") {
            val image = ProfileImage(id = 1L, url = "https://cdn/represent.jpg", sequence = 1L, fileSize = 0L, represent = true, createdAt = Instant.now())
            every { service.confirmRepresentImage(1L, "https://cdn/represent.jpg") } returns image

            Then("represent=true인 사진이 반환된다") {
                val result = controller.confirmRepresentImage(1L, ProfileRequest.ImageConfirm("https://cdn/represent.jpg"))
                result.represent shouldBe true
                result.url shouldBe "https://cdn/represent.jpg"
            }
        }
    }

    Given("추가 사진 등록 시") {
        When("서비스가 정상 처리하면") {
            val image = ProfileImage(id = 1L, url = "https://cdn/extra.jpg", sequence = 2L, fileSize = 0L, represent = false, createdAt = Instant.now())
            every { service.confirmAdditionalImage(1L, "https://cdn/extra.jpg") } returns image

            Then("represent=false인 사진이 반환된다") {
                val result = controller.confirmAdditionalImage(1L, ProfileRequest.ImageConfirm("https://cdn/extra.jpg"))
                result.represent shouldBe false
                result.url shouldBe "https://cdn/extra.jpg"
            }
        }

        When("이미지 개수 제한을 초과하면") {
            every { service.confirmAdditionalImage(1L, "https://cdn/over.jpg") } throws LanglezException(400, "profile.image.limit-exceeded")

            Then("BAD_REQUEST 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.confirmAdditionalImage(1L, ProfileRequest.ImageConfirm("https://cdn/over.jpg"))
                }
                ex.status shouldBe 400
            }
        }
    }

    Given("대표 사진 변경 시") {
        When("등록되지 않은 URL로 변경을 요청하면") {
            every { service.changeRepresentImage(1L, "https://cdn/ghost.jpg") } throws LanglezException(404, "profile.image.not-found")

            Then("NOT_FOUND 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.changeRepresentImage(1L, ProfileRequest.ImageConfirm("https://cdn/ghost.jpg"))
                }
                ex.status shouldBe 404
            }
        }

        When("서비스가 정상 처리하면") {
            val image = ProfileImage(id = 1L, url = "https://cdn/new.jpg", sequence = 2L, fileSize = 0L, represent = true, createdAt = Instant.now())
            every { service.changeRepresentImage(1L, "https://cdn/new.jpg") } returns image

            Then("변경된 대표 사진이 반환된다") {
                val result = controller.changeRepresentImage(1L, ProfileRequest.ImageConfirm("https://cdn/new.jpg"))
                result.represent shouldBe true
                result.url shouldBe "https://cdn/new.jpg"
            }
        }
    }

    Given("사진 삭제 시") {
        When("memberId와 url로 삭제하면") {
            every { service.deleteImage(1L, "https://cdn/delete.jpg") } just runs

            Then("서비스의 deleteImage가 정확히 호출된다") {
                controller.deleteImage(1L, "https://cdn/delete.jpg")
                verify(exactly = 1) { service.deleteImage(1L, "https://cdn/delete.jpg") }
            }
        }
    }
})

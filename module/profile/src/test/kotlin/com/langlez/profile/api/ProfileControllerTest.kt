package com.langlez.profile.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.GlobalRestControllerAdvice
import com.langlez.annotation.MemberIdResolver
import com.langlez.member.contract.MemberReader
import com.langlez.exception.LanglezException
import com.langlez.profile.application.ProfileService
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileImage
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.mockk.*
import org.springframework.context.support.ResourceBundleMessageSource
import org.springframework.security.authentication.TestingAuthenticationToken
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.http.MediaType
import java.time.Instant
import java.util.Locale

class ProfileControllerTest : BehaviorSpec({

    val service = mockk<ProfileService>()
    val controller = ProfileController(service)
    val locale = Locale.forLanguageTag("ko")

    afterEach { clearMocks(service, answers = false) }

    fun memberInfo(id: Long = 1L, handle: String = "testuser", gender: String = "SECRET") = MemberReader.ProfileInfo(
        id = id,
        handle = handle,
        gender = gender,
        locale = null,
        birthDay = null,
    )

    Given("프로필 상세 조회 시") {
        When("존재하는 username을 조회하면") {
            val detail = ProfileResponse.Detail(
                handle = "target",
                bio = "hello",
                goal = "goal",
                want = "want",
                gender = "MALE",
                mbti = "INTJ",
                locale = null,
                birthDay = null,
                visitCount = 5L,
                followerCount = 12L,
                followingCount = 3L,
            )
            every { service.getProfileDetail(1L, "target", locale) } returns detail

            Then("서비스 결과가 그대로 반환된다") {
                val result = controller.getProfile(1L, "target", locale)
                result.handle shouldBe "target"
                result.visitCount shouldBe 5L
                result.followerCount shouldBe 12L
                result.followingCount shouldBe 3L
            }
        }

        When("존재하지 않는 username을 조회하면") {
            every { service.getProfileDetail(1L, "ghost", locale) } throws LanglezException(404, "profile.not-found")

            Then("NOT_FOUND 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getProfile(1L, "ghost", locale)
                }
                ex.status.value() shouldBe 404
            }
        }
    }

    Given("내 프로필 수정 시") {
        When("서비스가 정상 처리하면") {
            val profile = Profile(id = 1L, bio = "new bio")
            val request = ProfileRequest.Update(bio = "new bio")
            val detail = ProfileResponse.ProfileDetail(profile, memberInfo(gender = "MALE"))
            every { service.updateProfile(1L, request, locale) } returns detail

            Then("변경된 프로필이 반환된다") {
                val result = controller.updateProfile(1L, request, locale)
                result.bio shouldBe "new bio"
                result.gender shouldBe "MALE"
            }
        }
    }

    Given("내 프로필 수정 요청의 검증 (MockMvc, @Valid + GlobalRestControllerAdvice 실동작)") {
        val mapper = ObjectMapper()
        val messageSource = ResourceBundleMessageSource().apply {
            setBasename("messages")
            setDefaultEncoding("UTF-8")
        }
        val mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(GlobalRestControllerAdvice(messageSource))
            .setCustomArgumentResolvers(MemberIdResolver())
            .build()

        beforeTest {
            SecurityContextHolder.getContext().authentication = TestingAuthenticationToken(1L, null)
        }
        afterTest {
            SecurityContextHolder.clearContext()
        }

        When("bio 가 201자로 상한(200자)을 넘으면") {
            val body = mapper.writeValueAsString(mapOf("bio" to "a".repeat(201)))

            Then("400 이 나고, 응답에 i18n 키 원문이 아니라 번역된 문장이 담긴다") {
                val result = mockMvc.perform(
                    patch("/api/v1/profiles/me")
                        .header("Accept-Language", "ko")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                ).andExpect(status().isBadRequest).andReturn()

                val response = result.response.contentAsString
                response shouldNotContain "validation.member.bio.size"
                response shouldContain "자기소개는 200자 이내여야 합니다"
            }
        }
    }

    Given("이미지 업로드 URL 발급 시") {
        When("정상적인 contentType으로 요청하면") {
            every { service.generateImageUploadUrl(1L, "photo.jpg", "image/jpeg") } returns
                com.langlez.attachment.contract.Storage.PresignedResult(key = "profiles/photo.jpg", presigned = "https://cdn/upload/photo.jpg")

            Then("업로드 URL이 반환된다") {
                val result = controller.getImageUploadUrl(1L, "photo.jpg", "image/jpeg")
                result.presigned shouldBe "https://cdn/upload/photo.jpg"
                result.key shouldBe "profiles/photo.jpg"
            }
        }

        When("이미지가 아닌 contentType으로 요청하면") {
            every { service.generateImageUploadUrl(1L, "video.mp4", "video/mp4") } throws LanglezException(400, "file.unsupported-content-type")

            Then("BAD_REQUEST 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getImageUploadUrl(1L, "video.mp4", "video/mp4")
                }
                ex.status.value() shouldBe 400
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
                ex.status.value() shouldBe 400
            }
        }
    }

    Given("대표 사진 변경 시") {
        When("등록되지 않은 URL로 변경을 요청하면") {
            every { service.changeRepresentImage(1L, "https://cdn/ghost.jpg") } throws LanglezException(404, "profile.image.not-found")

            Then("NOT_FOUND 예외가 전파된다") {
                val ex = shouldThrow<LanglezException> {
                    controller.changeRepresentImage(1L, ProfileRequest.ImageSelect("https://cdn/ghost.jpg"))
                }
                ex.status.value() shouldBe 404
            }
        }

        When("서비스가 정상 처리하면") {
            val image = ProfileImage(id = 1L, url = "https://cdn/new.jpg", sequence = 2L, fileSize = 0L, represent = true, createdAt = Instant.now())
            every { service.changeRepresentImage(1L, "https://cdn/new.jpg") } returns image

            Then("변경된 대표 사진이 반환된다") {
                val result = controller.changeRepresentImage(1L, ProfileRequest.ImageSelect("https://cdn/new.jpg"))
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

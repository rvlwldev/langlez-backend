package com.langlez.echo.api

import com.langlez.core.Storage
import com.langlez.echo.api.request.EchoCommentCreateRequest
import com.langlez.echo.api.request.EchoPostCreateRequest
import com.langlez.echo.application.EchoService
import com.langlez.echo.application.PostView
import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.Post
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.validation.Validation
import org.springframework.http.HttpStatus.FORBIDDEN
import java.time.Instant

class EchoControllerTest : BehaviorSpec({

    val service = mockk<EchoService>()
    val controller = EchoController(service)

    // @Valid 는 컨트롤러를 직접 부르는 단위 테스트에선 안 돈다. 제약 자체를 검증기로 확인한다.
    val validator = Validation.buildDefaultValidatorFactory().validator

    afterEach { clearMocks(service, answers = false) }

    fun view(id: Long = 1L, authorId: Long = 1L) = PostView(
        id = id,
        authorId = authorId,
        content = "내용",
        mediaUrls = emptyList(),
        likeCount = 0,
        liked = false,
        createdAt = Instant.EPOCH,
    )

    fun comment(id: Long = 1L, postId: Long = 1L, authorId: Long = 1L) =
        Comment(id = id, postId = postId, authorId = authorId, content = "댓글")

    Given("타임라인을 볼 때") {

        When("size 를 터무니없이 크게 보내면") {
            Then("홈 타임라인은 상한 50 으로 깎여서 서비스로 간다") {
                every { service.homeTimeline(1L, 50, null) } returns emptyList()

                controller.homeTimeline(memberId = 1L, size = 1_000_000, cursor = null)

                verify { service.homeTimeline(1L, 50, null) }
            }

            Then("댓글 목록도 상한 50 으로 깎인다") {
                every { service.listComments(1L, 9L, 50, null) } returns emptyList()

                controller.listComments(viewerId = 1L, postId = 9L, size = 1_000_000, cursor = null)

                verify { service.listComments(1L, 9L, 50, null) }
            }
        }

        When("size 를 0 이하로 보내면") {
            Then("최소 1 로 올라간다") {
                every { service.hashtagTimeline(1L, "seoul", 1, null) } returns emptyList()

                controller.hashtagTimeline(viewerId = 1L, tag = "seoul", size = 0, cursor = null)

                verify { service.hashtagTimeline(1L, "seoul", 1, null) }
            }
        }

        // 경로의 {memberId} 는 조회 대상이고 요청자는 @MemberId 다. 둘을 바꿔 넘기면
        // 남의 프로필을 열었는데 내 글이 나온다 — 컴파일도 되고 조용히 통과하는 종류라 고정한다.
        When("남의 타임라인을 열면") {
            Then("보는 사람과 조회 대상이 그 순서로 서비스에 전달된다") {
                every { service.memberTimeline(7L, 99L, 20, 30L) } returns listOf(view(id = 5L, authorId = 99L))

                val result = controller.memberTimeline(viewerId = 7L, authorId = 99L, size = 20, cursor = 30L)

                result.map { it.id } shouldBe listOf(5L)
                verify { service.memberTimeline(viewerId = 7L, authorId = 99L, size = 20, cursor = 30L) }
            }
        }

        When("커서를 넘기면") {
            Then("글 id 커서가 그대로 전달된다 (created_at 이 아니다)") {
                every { service.homeTimeline(1L, 20, 100L) } returns listOf(view())

                controller.homeTimeline(memberId = 1L, size = 20, cursor = 100L) shouldHaveSize 1

                verify { service.homeTimeline(1L, 20, 100L) }
            }
        }
    }

    Given("글을 쓸 때") {

        When("본문과 첨부 key 를 보내면") {
            Then("인증된 회원 id 와 함께 서비스로 간다") {
                every { service.createPost(7L, "안녕 #seoul", listOf("echo/a.jpg")) } returns view(id = 3L, authorId = 7L)

                val result = controller.createPost(
                    memberId = 7L,
                    request = EchoPostCreateRequest(content = "안녕 #seoul", keys = listOf("echo/a.jpg")),
                )

                result.id shouldBe 3L
                verify { service.createPost(memberId = 7L, content = "안녕 #seoul", keys = listOf("echo/a.jpg")) }
            }
        }

        When("본문이 상한을 넘으면") {
            Then("@Valid 가 걸러낸다 (서비스까지 가지 않는다)") {
                val request = EchoPostCreateRequest(content = "가".repeat(Post.MAX_CONTENT_LENGTH + 1))

                validator.validate(request).map { it.propertyPath.toString() } shouldBe listOf("content")
            }
        }

        When("첨부 key 가 상한을 넘으면") {
            Then("@Valid 가 걸러낸다") {
                val request = EchoPostCreateRequest(keys = List(Post.MAX_MEDIA_COUNT + 1) { "echo/$it.jpg" })

                validator.validate(request).map { it.propertyPath.toString() } shouldBe listOf("keys")
            }
        }

        When("첨부 key 원소가 빈 문자열이면") {
            Then("@Valid 가 걸러낸다") {
                validator.validate(EchoPostCreateRequest(keys = listOf(""))).shouldHaveSize(1)
            }
        }

        When("첨부 key 원소가 공백뿐이면") {
            Then("@Valid 가 걸러낸다") {
                validator.validate(EchoPostCreateRequest(keys = listOf("   "))).shouldHaveSize(1)
            }
        }
    }

    Given("글을 지울 때") {

        // 소유자 검사는 서비스에 있다. 컨트롤러가 경로의 글 id 를 소유자 자리에 넘기면
        // 검사가 "글 id == 글 id" 가 되어 통째로 무력화된다.
        When("삭제를 요청하면") {
            Then("인증된 회원 id 가 소유자 자리로, 경로 id 가 글 자리로 간다") {
                every { service.deletePost(7L, 42L) } returns Unit

                controller.deletePost(memberId = 7L, postId = 42L)

                verify { service.deletePost(memberId = 7L, postId = 42L) }
            }
        }

        When("남의 글이면") {
            Then("서비스의 403 을 그대로 올린다 (컨트롤러가 삼키지 않는다)") {
                every { service.deletePost(7L, 42L) } throws LanglezException(FORBIDDEN, "echo.post.not-owner")

                val ex = shouldThrow<LanglezException> { controller.deletePost(memberId = 7L, postId = 42L) }

                ex.status.value() shouldBe 403
            }
        }
    }

    Given("댓글을 다룰 때") {

        When("댓글을 달면") {
            Then("인증된 회원 id·글 id·본문이 그 순서로 전달된다") {
                every { service.comment(7L, 42L, "댓글") } returns comment(id = 8L, postId = 42L, authorId = 7L)

                val result = controller.comment(
                    memberId = 7L,
                    postId = 42L,
                    request = EchoCommentCreateRequest(content = "댓글"),
                )

                result.id shouldBe 8L
                verify { service.comment(memberId = 7L, postId = 42L, content = "댓글") }
            }
        }

        When("본문이 비어 있으면") {
            Then("@Valid 가 걸러낸다") {
                validator.validate(EchoCommentCreateRequest(content = " "))
                    .map { it.propertyPath.toString() } shouldBe listOf("content")
            }
        }

        When("남의 댓글을 지우려 하면") {
            Then("서비스의 403 을 그대로 올린다") {
                every { service.deleteComment(7L, 8L) } throws LanglezException(FORBIDDEN, "echo.comment.not-owner")

                val ex = shouldThrow<LanglezException> { controller.deleteComment(memberId = 7L, commentId = 8L) }

                ex.status.value() shouldBe 403
            }
        }
    }

    Given("좋아요를 누를 때") {

        When("좋아요와 취소를 요청하면") {
            Then("인증된 회원 id 가 먼저, 글 id 가 뒤로 간다") {
                every { service.like(7L, 42L) } returns Unit
                every { service.unlike(7L, 42L) } returns Unit

                controller.like(memberId = 7L, postId = 42L)
                controller.unlike(memberId = 7L, postId = 42L)

                verify { service.like(memberId = 7L, postId = 42L) }
                verify { service.unlike(memberId = 7L, postId = 42L) }
            }
        }
    }

    Given("첨부 업로드 URL 을 받을 때") {

        When("파일명과 Content-Type 을 보내면") {
            Then("key 와 업로드 URL 을 함께 돌려준다 (클라이언트가 준 URL 을 저장하는 경로가 없다)") {
                every { service.presignUpload(7L, "photo.jpg", "image/jpeg") } returns
                    Storage.PresignedResult("echo/2026/photo.jpg", "https://s3/put?sig=1")

                val result = controller.getUploadUrl(memberId = 7L, filename = "photo.jpg", contentType = "image/jpeg")

                result.key shouldBe "echo/2026/photo.jpg"
                verify { service.presignUpload(memberId = 7L, filename = "photo.jpg", contentType = "image/jpeg") }
            }
        }
    }
})

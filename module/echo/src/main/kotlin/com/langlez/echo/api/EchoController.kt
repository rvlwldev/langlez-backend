package com.langlez.echo.api

import com.langlez.annotation.MemberId
import com.langlez.core.Storage
import com.langlez.echo.api.request.EchoCommentCreateRequest
import com.langlez.echo.api.request.EchoPostCreateRequest
import com.langlez.echo.api.response.EchoCommentResponse
import com.langlez.echo.api.response.EchoPostResponse
import com.langlez.echo.application.EchoService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/echoes")
class EchoController(private val service: EchoService) : EchoAPI {

    @PostMapping
    override fun createPost(
        @MemberId memberId: Long,
        @RequestBody @Valid request: EchoPostCreateRequest,
    ): EchoPostResponse = EchoPostResponse(service.createPost(memberId, request.content, request.keys))

    @DeleteMapping("/{postId}")
    @ResponseStatus(NO_CONTENT)
    override fun deletePost(@MemberId memberId: Long, @PathVariable postId: Long) {
        service.deletePost(memberId, postId)
    }

    @GetMapping("/me/timeline")
    override fun homeTimeline(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<EchoPostResponse> = service.homeTimeline(memberId, size.coerceIn(1, MAX_SIZE), cursor).map(::EchoPostResponse)

    @GetMapping("/members/{memberId}")
    override fun memberTimeline(
        @MemberId viewerId: Long,
        @PathVariable("memberId") authorId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<EchoPostResponse> = service.memberTimeline(viewerId, authorId, size.coerceIn(1, MAX_SIZE), cursor).map(::EchoPostResponse)

    @GetMapping("/hashtags/{tag}")
    override fun hashtagTimeline(
        @MemberId viewerId: Long,
        @PathVariable tag: String,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<EchoPostResponse> = service.hashtagTimeline(viewerId, tag, size.coerceIn(1, MAX_SIZE), cursor).map(::EchoPostResponse)

    @GetMapping("/{postId}")
    override fun getPost(@MemberId viewerId: Long, @PathVariable postId: Long): EchoPostResponse =
        EchoPostResponse(service.getPost(viewerId, postId))

    @PostMapping("/{postId}/likes")
    @ResponseStatus(NO_CONTENT)
    override fun like(@MemberId memberId: Long, @PathVariable postId: Long) {
        service.like(memberId, postId)
    }

    @DeleteMapping("/{postId}/likes")
    @ResponseStatus(NO_CONTENT)
    override fun unlike(@MemberId memberId: Long, @PathVariable postId: Long) {
        service.unlike(memberId, postId)
    }

    @PostMapping("/{postId}/comments")
    override fun comment(
        @MemberId memberId: Long,
        @PathVariable postId: Long,
        @RequestBody @Valid request: EchoCommentCreateRequest,
    ): EchoCommentResponse = EchoCommentResponse(service.comment(memberId, postId, request.content))

    @GetMapping("/{postId}/comments")
    override fun listComments(
        @MemberId viewerId: Long,
        @PathVariable postId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<EchoCommentResponse> =
        service.listComments(viewerId, postId, size.coerceIn(1, MAX_SIZE), cursor).map(::EchoCommentResponse)

    // 댓글은 글 하위로 두지 않는다. 삭제·수정에 글 id 가 필요 없는데 경로로 받으면
    // 클라이언트가 아무 글 id나 붙여도 통과해 "글 하위"라는 계약이 거짓이 된다.
    @DeleteMapping("/comments/{commentId}")
    @ResponseStatus(NO_CONTENT)
    override fun deleteComment(@MemberId memberId: Long, @PathVariable commentId: Long) {
        service.deleteComment(memberId, commentId)
    }

    // `upload-url` 은 리터럴이라 한 세그먼트짜리 `{postId}` 패턴보다 먼저 매칭된다. 경로가 겹치지 않는다.
    @GetMapping("/upload-url")
    override fun getUploadUrl(
        @MemberId memberId: Long,
        @RequestParam filename: String,
        @RequestParam contentType: String,
    ): Storage.PresignedResult = service.presignUpload(memberId, filename, contentType)

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 피드를 통째로 긁어갈 수 있다. 글 목록은 한 건마다
        // 차단 여부를 따로 묻기 때문에(EchoService.toViews) 페이지가 커지면 그만큼 조회가 늘어난다.
        private const val MAX_SIZE = 50
    }
}

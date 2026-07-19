package com.langlez.echo.api

import com.langlez.echo.application.EchoService
import com.langlez.security.web.MemberID
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/echoes")
class EchoController(
    private val service: EchoService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPost(
        @MemberID memberId: Long,
        @RequestBody @Valid request: EchoRequest.CreatePost
    ): EchoResponse.PostDto {
        val mediaPairs = request.media.map { it.url to it.type }
        return service.createPost(memberId, request.content, mediaPairs)
    }

    @GetMapping("/feed/following")
    fun getFollowingFeed(
        @MemberID memberId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): EchoResponse.CursorList = service.getFollowingFeed(memberId, cursor, size)

    @GetMapping("/feed/recommended")
    fun getRecommendedFeed(
        @MemberID memberId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): EchoResponse.CursorList = service.getRecommendedFeed(memberId, cursor, size)

    @PostMapping("/{postId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun likePost(
        @MemberID memberId: Long,
        @PathVariable postId: Long
    ) {
        service.likePost(memberId, postId)
    }

    @DeleteMapping("/{postId}/like")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unlikePost(
        @MemberID memberId: Long,
        @PathVariable postId: Long
    ) {
        service.unlikePost(memberId, postId)
    }

    @PostMapping("/{postId}/report")
    @ResponseStatus(HttpStatus.CREATED)
    fun reportPost(
        @MemberID memberId: Long,
        @PathVariable postId: Long,
        @RequestBody @Valid request: EchoRequest.ReportPost
    ) {
        service.reportPost(memberId, postId, request.reason)
    }

    @GetMapping("/hashtags/{tag}")
    fun searchByHashtag(
        @PathVariable tag: String,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): EchoResponse.CursorList = service.searchByHashtag(tag, cursor, size)

    @GetMapping("/hashtags/trending")
    fun getTrendingHashtags(
        @RequestParam days: Int,
        @RequestParam(defaultValue = "10") limit: Int
    ): List<EchoResponse.TrendingHashtag> = service.getTrendingHashtags(days, limit)

    @DeleteMapping("/{postId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePost(
        @MemberID memberId: Long,
        @PathVariable postId: Long
    ) {
        service.deletePost(memberId, postId)
    }

    @PostMapping("/{postId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    fun createComment(
        @MemberID memberId: Long,
        @PathVariable postId: Long,
        @RequestBody @Valid request: EchoRequest.CreateComment
    ): EchoResponse.CommentDto {
        return service.addComment(memberId, postId, request.content)
    }

    @DeleteMapping("/{postId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteComment(
        @MemberID memberId: Long,
        @PathVariable postId: Long,
        @PathVariable commentId: Long
    ) {
        service.deleteComment(memberId, postId, commentId)
    }

    @GetMapping("/{postId}/comments")
    fun getComments(
        @PathVariable postId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): EchoResponse.CommentCursorList = service.getComments(postId, cursor, size)
}

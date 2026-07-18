package com.langlez.echo.api

import com.langlez.core.LanglezException
import com.langlez.echo.application.EchoService
import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostRepository
import com.langlez.member.domain.MemberRepository
import com.langlez.security.web.MemberID
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/echoes")
class EchoController(
    private val service: EchoService,
    private val memberRepo: MemberRepository,
    private val postRepository: PostRepository,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPost(
        @MemberID memberId: Long,
        @RequestBody @Valid request: EchoRequest.CreatePost
    ): EchoResponse.PostDto {
        val mediaPairs = request.media.map { it.url to it.type }
        val post = service.createPost(memberId, request.content, mediaPairs)
        return toPostDto(post)
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

    private fun toPostDto(post: Post): EchoResponse.PostDto {
        val author = memberRepo.findById(post.authorId)
            ?: throw LanglezException(404, "member.not-found")
        val media = postRepository.findMediaByPostId(post.id)
            .map { EchoResponse.PostMediaDto(it.url, it.type) }
        return EchoResponse.PostDto(
            postId = post.id,
            username = author.username,
            nickname = author.nickname,
            content = post.content,
            media = media,
            likeCount = post.likeCount,
            createdAt = post.createdAt
        )
    }
}

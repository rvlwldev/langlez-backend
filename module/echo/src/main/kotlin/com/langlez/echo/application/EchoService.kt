package com.langlez.echo.application

import com.langlez.core.BlockQuery
import com.langlez.core.FollowQuery
import com.langlez.core.Storage
import com.langlez.core.event.echo.EchoCommentCreatedEvent
import com.langlez.core.event.echo.EchoPostLikedEvent
import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.EchoRepository
import com.langlez.echo.domain.Hashtag
import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostMedia
import com.langlez.exception.LanglezException
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 피드 유스케이스.
 *
 * 남의 것을 건드리는 동작(글·댓글 삭제)은 반드시 작성자인지부터 확인한다. id 는 클라이언트가 그대로
 * 보내는 값이라 한 군데라도 빼면 아무나 남의 글을 지운다(IDOR).
 *
 * 조회는 모두 `BlockQuery` 를 거친다. 차단은 "안 보여주기"가 전부라, 거르는 곳을 하나라도 빠뜨리면
 * 차단이 없는 것과 같아진다.
 */
@Service
class EchoService(
    private val repo: EchoRepository,
    /**
     * relationship 모듈이 `FollowQuery` 를 제공하기 전까지는 빈이 없을 수 있어 nullable 이다.
     * 없을 때 빈 타임라인을 돌려주면 "팔로우가 0명"과 구분이 안 되므로 503 으로 드러낸다.
     */
    private val follows: FollowQuery?,
    private val blocks: BlockQuery,
    private val storage: Storage,
    private val publisher: ApplicationEventPublisher,
    private val tx: TransactionTemplate,
) {

    /**
     * 글 작성.
     *
     * 첨부는 **key 로만** 확정한다. 클라이언트가 준 URL 을 그대로 저장하면 외부 주소를 심을 수 있고
     * 서명이 붙은 업로드 URL 이 그대로 남는다. `storage.attach(key)` 가 돌려준 조회용 URL 만 쓴다.
     *
     * attach 는 스토리지 왕복이 걸린 블로킹 I/O 라 DB 커넥션을 쥐기 전에 끝낸다.
     */
    fun createPost(memberId: Long, content: String, keys: List<String>): PostView {
        if (content.isBlank() && keys.isEmpty()) throw LanglezException(BAD_REQUEST, "echo.post.empty")
        if (content.length > Post.MAX_CONTENT_LENGTH) throw LanglezException(BAD_REQUEST, "echo.post.too-long")
        if (keys.size > Post.MAX_MEDIA_COUNT) throw LanglezException(BAD_REQUEST, "echo.post.media-limit-exceeded")

        val urls = keys.map { storage.attach(it) }

        val post = requireNotNull(
            tx.execute {
                repo.save(Post(authorId = memberId, content = content)).also { saved ->
                    if (urls.isNotEmpty()) repo.saveMedia(
                        urls.mapIndexed { i, url -> PostMedia(postId = saved.id, url = url, type = mediaType(keys[i]), sequence = i) }
                    )
                    linkHashtags(saved)
                }
            }
        )

        return PostView(post, urls, liked = false)
    }

    @Transactional
    fun deletePost(memberId: Long, postId: Long) {
        val post = findPostOrThrow(postId)
        if (post.authorId != memberId) throw LanglezException(FORBIDDEN, "echo.post.not-owner")

        repo.save(post.apply { delete() })
    }

    /** 홈 타임라인 — 내가 팔로우한 사람의 글. */
    @Transactional(readOnly = true)
    fun homeTimeline(memberId: Long, size: Int, cursor: Long?): List<PostView> {
        val following = follows?.followingIds(memberId)
            ?: throw LanglezException(SERVICE_UNAVAILABLE, "echo.timeline.unavailable")

        if (following.isEmpty()) return emptyList()

        return toViews(memberId, repo.findPosts(following, size, cursor))
    }

    /** 특정 회원의 글. 차단 관계면 목록 자체를 막는다 — 걸러 봐야 전부 빠진다. */
    @Transactional(readOnly = true)
    fun memberTimeline(viewerId: Long, authorId: Long, size: Int, cursor: Long?): List<PostView> {
        if (isBlocked(viewerId, authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        return toViews(viewerId, repo.findPosts(listOf(authorId), size, cursor))
    }

    @Transactional(readOnly = true)
    fun hashtagTimeline(viewerId: Long, tag: String, size: Int, cursor: Long?): List<PostView> =
        toViews(viewerId, repo.findPostsByHashtag(tag.removePrefix("#").lowercase(), size, cursor))

    @Transactional(readOnly = true)
    fun getPost(viewerId: Long, postId: Long): PostView {
        val post = findPostOrThrow(postId)
        if (isBlocked(viewerId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        return toViews(viewerId, listOf(post)).firstOrNull()
            ?: throw LanglezException(NOT_FOUND, "echo.post.not-found")
    }

    @Transactional
    fun like(memberId: Long, postId: Long) {
        val post = findPostOrThrow(postId)
        if (isBlocked(memberId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        // 유니크 제약이 최종 방어선이지만, 제약 위반은 트랜잭션을 통째로 망가뜨린다. 먼저 확인해서 409 로 돌려준다.
        if (repo.isLiked(postId, memberId)) throw LanglezException(CONFLICT, "echo.like.duplicated")

        repo.addLike(postId, memberId)

        // 자기 글에 자기가 누른 건 알릴 이유가 없다.
        if (post.authorId != memberId) publisher.publishEvent(EchoPostLikedEvent(postId, post.authorId, memberId))
    }

    /** 안 누른 상태에서 또 눌러도 그냥 넘어간다 — 결과(좋아요 아님)가 같은데 에러를 낼 이유가 없다. */
    @Transactional
    fun unlike(memberId: Long, postId: Long) {
        val post = findPostOrThrow(postId)
        if (repo.isLiked(post.id, memberId)) repo.removeLike(post.id, memberId)
    }

    @Transactional
    fun comment(memberId: Long, postId: Long, content: String): Comment {
        if (content.isBlank()) throw LanglezException(BAD_REQUEST, "echo.comment.empty")
        if (content.length > Comment.MAX_CONTENT_LENGTH) throw LanglezException(BAD_REQUEST, "echo.comment.too-long")

        val post = findPostOrThrow(postId)
        if (isBlocked(memberId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        val saved = repo.save(Comment(postId = postId, authorId = memberId, content = content))

        if (post.authorId != memberId) publisher.publishEvent(
            EchoCommentCreatedEvent(postId, post.authorId, saved.id, memberId, content.take(PREVIEW_LENGTH))
        )

        return saved
    }

    @Transactional(readOnly = true)
    fun listComments(viewerId: Long, postId: Long, size: Int, cursor: Long?): List<Comment> {
        val post = findPostOrThrow(postId)
        if (isBlocked(viewerId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        return repo.findComments(postId, size, cursor).filterNot { isBlocked(viewerId, it.authorId) }
    }

    @Transactional
    fun deleteComment(memberId: Long, commentId: Long) {
        val comment = repo.findComment(commentId) ?: throw LanglezException(NOT_FOUND, "echo.comment.not-found")
        if (comment.authorId != memberId) throw LanglezException(FORBIDDEN, "echo.comment.not-owner")

        repo.save(comment.apply { delete() })
    }

    /**
     * 첨부 업로드 URL 발급.
     *
     * key 를 함께 내려줘야 클라이언트가 서명 붙은 PUT URL 대신 key 로 확정할 수 있다.
     * contentType 은 믿지 않는다 — 피드 첨부는 사진·영상뿐이다.
     */
    fun presignUpload(memberId: Long, filename: String, contentType: String): Storage.PresignedResult {
        val type = when {
            contentType.startsWith("image/") -> Storage.Type.IMAGE
            contentType.startsWith("video/") -> Storage.Type.VIDEO
            else -> throw LanglezException(BAD_REQUEST, "echo.attachment.unsupported-type")
        }

        return storage.presign(memberId, SOURCE, type, filename)
    }

    private fun linkHashtags(post: Post) {
        val names = Hashtag.extract(post.content)
        if (names.isEmpty()) return

        repo.linkHashtags(post.id, repo.findOrCreateHashtags(names).map(Hashtag::id))
    }

    /**
     * 글 목록에 첨부와 내 좋아요 여부를 붙인다.
     *
     * 차단은 여기서 한 번에 거른다 — 목록마다 따로 걸면 빠뜨리는 곳이 생긴다.
     * ponytail: 페이지의 작성자 수만큼 `isBlockedBetween` 이 나간다(페이지 크기 상한이 있어 최대 수십 번).
     * 느려지면 `BlockQuery` 에 id 목록을 한 번에 묻는 메서드를 추가하는 쪽으로 올린다.
     */
    private fun toViews(viewerId: Long, posts: List<Post>): List<PostView> {
        val visible = posts.filterNot { isBlocked(viewerId, it.authorId) }
        if (visible.isEmpty()) return emptyList()

        val ids = visible.map(Post::id)
        val media = repo.findMedia(ids).groupBy(PostMedia::postId)
        val liked = repo.findLikedPostIds(viewerId, ids)

        return visible.map { post ->
            PostView(
                post = post,
                mediaUrls = media[post.id].orEmpty().sortedBy(PostMedia::sequence).map(PostMedia::url),
                liked = post.id in liked,
            )
        }
    }

    private fun isBlocked(viewerId: Long, authorId: Long) =
        viewerId != authorId && blocks.isBlockedBetween(viewerId, authorId)

    private fun findPostOrThrow(postId: Long) = repo.findPost(postId)
        ?: throw LanglezException(NOT_FOUND, "echo.post.not-found")

    /** 확정된 첨부의 종류. 표시용 힌트라 key 의 확장자로 충분하다 — 접근 제어는 스토리지가 한다. */
    private fun mediaType(key: String) =
        if (key.substringAfterLast('.', "").lowercase() in VIDEO_EXTENSIONS) PostMedia.Type.VIDEO
        else PostMedia.Type.IMAGE

    companion object {
        const val SOURCE = "echo"
        private const val PREVIEW_LENGTH = 50
        private val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm", "m4v", "avi", "mkv")
    }
}

/** 글 + 첨부 + 보는 사람의 좋아요 여부. 엔티티를 그대로 내보내면 blinded/deletedAt 까지 실려 나간다. */
data class PostView(
    val id: Long,
    val authorId: Long,
    val content: String,
    val mediaUrls: List<String>,
    val likeCount: Long,
    val liked: Boolean,
    val createdAt: Instant,
) {
    constructor(post: Post, mediaUrls: List<String>, liked: Boolean) : this(
        id = post.id,
        authorId = post.authorId,
        content = post.content,
        mediaUrls = mediaUrls,
        likeCount = post.likeCount,
        liked = liked,
        createdAt = post.createdAt,
    )
}

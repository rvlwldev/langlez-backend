package com.langlez.echo.application

import com.langlez.attachment.contract.Storage
import com.langlez.block.contract.BlockReader
import com.langlez.echo.contract.EchoCommentCreatedEvent
import com.langlez.echo.contract.EchoPostLikedEvent
import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.EchoRepository
import com.langlez.echo.domain.Hashtag
import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostMedia
import com.langlez.exception.LanglezException
import com.langlez.follow.contract.FollowReader
import java.time.Instant
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.CONFLICT
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 피드 유스케이스.
 *
 * 남의 것을 건드리는 동작(글·댓글 삭제)은 반드시 작성자인지부터 확인한다. id 는 클라이언트가 그대로
 * 보내는 값이라 한 군데라도 빼면 아무나 남의 글을 지운다(IDOR).
 *
 * 조회는 모두 `BlockReader` 를 거친다. 차단은 "안 보여주기"가 전부라, 거르는 곳을 하나라도 빠뜨리면
 * 차단이 없는 것과 같아진다.
 */
@Service
class EchoService(
    private val repo: EchoRepository,
    /**
     * follow 모듈이 `FollowReader` 를 제공하기 전까지는 빈이 없을 수 있어 nullable 이다.
     * 없을 때 빈 타임라인을 돌려주면 "팔로우가 0명"과 구분이 안 되므로 503 으로 드러낸다.
     */
    private val follows: FollowReader?,
    private val blocks: BlockReader,
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

    /**
     * 홈 타임라인 — 내가 팔로우한 사람의 글.
     *
     * 조회 경로에는 트랜잭션을 걸지 않는다. `follows`·`blocks` 는 `follow-api`·`block-api` 포트라
     * 곧 원격이 되는데, 트랜잭션 안이면 DB 커넥션을 쥔 채 그 왕복을 기다린다.
     * 감싸도 한 스냅샷이 되지 않는다 — 팔로우·차단은 이미 다른 모듈의 데이터다.
     */
    fun homeTimeline(memberId: Long, size: Int, cursor: Long?): List<PostView> {
        val following = follows?.followingIds(memberId)
            ?: throw LanglezException(SERVICE_UNAVAILABLE, "echo.timeline.unavailable")

        if (following.isEmpty()) return emptyList()

        val posts = fillPosts(memberId, size, cursor) { chunk, c -> repo.findPosts(following, chunk, c) }
        return enrich(memberId, posts)
    }

    /** 특정 회원의 글. 차단 관계면 목록 자체를 막는다 — 걸러 봐야 전부 빠진다. */
    fun memberTimeline(viewerId: Long, authorId: Long, size: Int, cursor: Long?): List<PostView> {
        if (isBlocked(viewerId, authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        val posts = fillPosts(viewerId, size, cursor) { chunk, c -> repo.findPosts(listOf(authorId), chunk, c) }
        return enrich(viewerId, posts)
    }

    fun hashtagTimeline(viewerId: Long, tag: String, size: Int, cursor: Long?): List<PostView> {
        val name = tag.removePrefix("#").lowercase()
        val posts = fillPosts(viewerId, size, cursor) { chunk, c -> repo.findPostsByHashtag(name, chunk, c) }
        return enrich(viewerId, posts)
    }

    fun getPost(viewerId: Long, postId: Long): PostView {
        val post = findPostOrThrow(postId)
        if (isBlocked(viewerId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        return enrich(viewerId, listOf(post)).firstOrNull()
            ?: throw LanglezException(NOT_FOUND, "echo.post.not-found")
    }

    /**
     * 차단 판정은 포트라 트랜잭션 밖에서 먼저 끝낸다. 판정과 저장 사이에 차단이 걸리면
     * 좋아요 한 건이 남는데, 그 뒤 조회는 전부 다시 차단을 보므로 상대 화면에 뜨지 않는다.
     */
    fun like(memberId: Long, postId: Long) {
        val post = findPostOrThrow(postId)
        if (isBlocked(memberId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        tx.execute {
            // 유니크 제약이 최종 방어선이지만, 제약 위반은 트랜잭션을 통째로 망가뜨린다. 먼저 확인해서 409 로 돌려준다.
            if (repo.isLiked(postId, memberId)) throw LanglezException(CONFLICT, "echo.like.duplicated")

            repo.addLike(postId, memberId)

            // 자기 글에 자기가 누른 건 알릴 이유가 없다.
            if (post.authorId != memberId) publisher.publishEvent(EchoPostLikedEvent(postId, post.authorId, memberId))
        }
    }

    /**
     * 안 누른 상태에서 또 눌러도 그냥 넘어간다 — 결과(좋아요 아님)가 같은데 에러를 낼 이유가 없다.
     *
     * 다른 메서드와 달리 차단 여부를 검사하지 않는다. 좋아요 취소는 내 반응을 지우는 동작이라 상대에게
     * 새로 노출되는 게 없다 — `like`/`comment` 처럼 상대에게 알림이 가거나 새 콘텐츠가 생기는 것과
     * 다르다. 차단을 걸거나 걸린 뒤에도 예전에 눌러둔 좋아요는 정리할 수 있어야 한다. 다음에 이 코드를
     * 보고 "빠뜨렸다"고 판단해 검사를 추가하지 않도록 남긴다.
     */
    @Transactional
    fun unlike(memberId: Long, postId: Long) {
        val post = findPostOrThrow(postId)
        if (repo.isLiked(post.id, memberId)) repo.removeLike(post.id, memberId)
    }

    /** `like` 와 같은 이유로 차단 판정이 트랜잭션 밖이다. */
    fun comment(memberId: Long, postId: Long, content: String): Comment {
        if (content.isBlank()) throw LanglezException(BAD_REQUEST, "echo.comment.empty")
        if (content.length > Comment.MAX_CONTENT_LENGTH) throw LanglezException(BAD_REQUEST, "echo.comment.too-long")

        val post = findPostOrThrow(postId)
        if (isBlocked(memberId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        return tx.execute {
            val saved = repo.save(Comment(postId = postId, authorId = memberId, content = content))

            if (post.authorId != memberId) publisher.publishEvent(
                EchoCommentCreatedEvent(postId, post.authorId, saved.id, memberId, content.take(PREVIEW_LENGTH))
            )

            saved
        }!!
    }

    fun listComments(viewerId: Long, postId: Long, size: Int, cursor: Long?): List<Comment> {
        val post = findPostOrThrow(postId)
        if (isBlocked(viewerId, post.authorId)) throw LanglezException(FORBIDDEN, "echo.blocked")

        return fillComments(viewerId, size, cursor) { chunk, c -> repo.findComments(postId, chunk, c) }
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

    /** 글 목록에 첨부와 내 좋아요 여부를 붙인다. 필터링은 하지 않는다 — 호출부가 이미 거른 목록만 넘긴다. */
    private fun enrich(viewerId: Long, posts: List<Post>): List<PostView> {
        if (posts.isEmpty()) return emptyList()

        val ids = posts.map(Post::id)
        val media = repo.findMedia(ids).groupBy(PostMedia::postId)
        val liked = repo.findLikedPostIds(viewerId, ids)

        return posts.map { post ->
            PostView(
                post = post,
                mediaUrls = media[post.id].orEmpty().sortedBy(PostMedia::sequence).map(PostMedia::url),
                liked = post.id in liked,
            )
        }
    }

    /**
     * 차단된 작성자의 글/댓글을 뺀 뒤에도 요청한 size 만큼 채운다.
     *
     * 저장소가 먼저 size 만큼 가져오고 그 다음에 차단을 거르면, 걸러진 만큼 페이지가 짧아진다 —
     * 클라이언트 입장에선 스크롤이 튀거나 심하면 빈 페이지가 와서 끝으로 오해한다.
     *
     * echo 는 block 모듈의 차단 테이블을 알지 못해 쿼리에서 직접 차단을 뺄 수 없다(모듈 간 테이블
     * 직접 조인 금지). `BlockReader` 포트도 단건 확인(`isBlockedBetween`)만 제공한다. 그래서 쿼리는 그대로
     * 두고, 부족하면 마지막으로 가져온 항목의 id 를 커서 삼아 다음 페이지를 이어서 가져오는 방식
     * (over-fetch)을 택했다.
     *
     * 성능: 차단 비율이 낮은 보통의 경우 라운드트립 1회로 끝난다 — 저장소가 요청한 개수보다 적게
     * 돌려주면 더 가져올 데이터가 없다는 뜻이라 그 자리에서 멈춘다. 차단한 사람들이 유독 활발히 쓰는
     * 것처럼 병적인 경우에만 라운드트립이 늘어나는데, [MAX_FILL_ROUNDS] 로 상한을 둬서 무한정 반복하지
     * 않는다 — 상한을 넘기면 요청한 size 보다 짧은 목록을 돌려준다(원래 버그보다는 낫지만 완전한
     * 보장은 아니다).
     *
     * 차단 판정은 라운드마다 `blockedAmong` 한 번이다. 항목마다 `isBlockedBetween` 을 부르면
     * 이 포트가 원격이 될 때 페이지 크기만큼 왕복이 생긴다.
     */
    private fun <T> fill(
        viewerId: Long,
        size: Int,
        cursor: Long?,
        authorIdOf: (T) -> Long,
        idOf: (T) -> Long,
        fetch: (chunk: Int, cursor: Long?) -> List<T>,
    ): List<T> {
        val result = mutableListOf<T>()
        var nextCursor = cursor
        var round = 0

        while (result.size < size && round < MAX_FILL_ROUNDS) {
            val chunk = size - result.size
            val page = fetch(chunk, nextCursor)
            if (page.isEmpty()) break

            val blocked = blocks.blockedAmong(viewerId, page.map(authorIdOf).toSet())
            result += page.filterNot { authorIdOf(it) in blocked }
            nextCursor = idOf(page.last())
            round++

            if (page.size < chunk) break
        }

        return result
    }

    private fun fillPosts(viewerId: Long, size: Int, cursor: Long?, fetch: (Int, Long?) -> List<Post>): List<Post> =
        fill(viewerId, size, cursor, Post::authorId, Post::id, fetch)

    private fun fillComments(
        viewerId: Long,
        size: Int,
        cursor: Long?,
        fetch: (Int, Long?) -> List<Comment>,
    ): List<Comment> = fill(viewerId, size, cursor, Comment::authorId, Comment::id, fetch)

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
        private const val MAX_FILL_ROUNDS = 3
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

package com.langlez.echo.infrastructure

import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.EchoRepository
import com.langlez.echo.domain.Hashtag
import com.langlez.echo.domain.HashtagDailyStat
import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostHashtag
import com.langlez.echo.domain.PostLike
import com.langlez.echo.domain.PostMedia
import com.langlez.echo.infrastructure.jpa.CommentJpaRepository
import com.langlez.echo.infrastructure.jpa.HashtagDailyStatJpaRepository
import com.langlez.echo.infrastructure.jpa.HashtagJpaRepository
import com.langlez.echo.infrastructure.jpa.PostHashtagJpaRepository
import com.langlez.echo.infrastructure.jpa.PostJpaRepository
import com.langlez.echo.infrastructure.jpa.PostLikeJpaRepository
import com.langlez.echo.infrastructure.jpa.PostMediaJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.ZoneOffset
import com.langlez.echo.domain.QComment.Companion.comment as QComment
import com.langlez.echo.domain.QHashtag.Companion.hashtag as QHashtag
import com.langlez.echo.domain.QPost.Companion.post as QPost
import com.langlez.echo.domain.QPostHashtag.Companion.postHashtag as QPostHashtag
import com.langlez.echo.domain.QPostLike.Companion.postLike as QPostLike

/**
 * 피드 저장소 어댑터.
 *
 * 캐시를 두지 않는다. 타임라인은 글이 들어올 때마다 첫 페이지가 통째로 바뀌고 좋아요 수는 계속 오른다 —
 * 캐시가 맞을 틈이 거의 없다.
 */
@Repository
class EchoRepositoryImpl(
    private val posts: PostJpaRepository,
    private val comments: CommentJpaRepository,
    private val likes: PostLikeJpaRepository,
    private val media: PostMediaJpaRepository,
    private val hashtags: HashtagJpaRepository,
    private val postHashtags: PostHashtagJpaRepository,
    private val stats: HashtagDailyStatJpaRepository,
    private val dsl: JPAQueryFactory,
) : EchoRepository {

    override fun save(post: Post): Post = posts.save(post)

    override fun findPost(id: Long): Post? = posts.findByIdAndDeletedAtIsNull(id)

    override fun findPosts(authorIds: Collection<Long>, size: Int, cursor: Long?): List<Post> {
        if (authorIds.isEmpty()) return emptyList()

        return dsl.selectFrom(QPost)
            .where(
                QPost.authorId.`in`(authorIds.toSet()),
                QPost.deletedAt.isNull,
                QPost.blinded.isFalse,
                cursor?.let(QPost.id::lt),
            )
            .orderBy(QPost.id.desc())
            .limit(size.toLong())
            .fetch()
    }

    override fun findPostsByHashtag(tag: String, size: Int, cursor: Long?): List<Post> =
        dsl.selectFrom(QPost)
            .join(QPostHashtag).on(QPostHashtag.postId.eq(QPost.id))
            .join(QHashtag).on(QHashtag.id.eq(QPostHashtag.hashtagId), QHashtag.name.eq(tag))
            .where(QPost.deletedAt.isNull, QPost.blinded.isFalse, cursor?.let(QPost.id::lt))
            .orderBy(QPost.id.desc())
            .limit(size.toLong())
            .fetch()

    override fun saveMedia(media: Collection<PostMedia>) {
        this.media.saveAll(media)
    }

    override fun findMedia(postIds: Collection<Long>): List<PostMedia> =
        if (postIds.isEmpty()) emptyList() else media.findAllByPostIdIn(postIds.toSet())

    override fun isLiked(postId: Long, memberId: Long): Boolean =
        likes.existsByPostIdAndMemberId(postId, memberId)

    override fun findLikedPostIds(memberId: Long, postIds: Collection<Long>): Set<Long> {
        if (postIds.isEmpty()) return emptySet()

        return dsl.select(QPostLike.postId)
            .from(QPostLike)
            .where(QPostLike.memberId.eq(memberId), QPostLike.postId.`in`(postIds.toSet()))
            .fetch()
            .toSet()
    }

    @Transactional
    override fun addLike(postId: Long, memberId: Long) {
        likes.save(PostLike(postId = postId, memberId = memberId))
        posts.increaseLikeCount(postId)
    }

    @Transactional
    override fun removeLike(postId: Long, memberId: Long) {
        likes.deleteByPostIdAndMemberId(postId, memberId)
        posts.decreaseLikeCount(postId)
    }

    override fun save(comment: Comment): Comment = comments.save(comment)

    override fun findComment(id: Long): Comment? = comments.findByIdAndDeletedAtIsNull(id)

    /** 댓글은 오래된 순으로 읽는 게 자연스럽다. 커서는 여기서도 id — 시계가 아니라 시퀀스다. */
    override fun findComments(postId: Long, size: Int, cursor: Long?): List<Comment> =
        dsl.selectFrom(QComment)
            .where(QComment.postId.eq(postId), QComment.deletedAt.isNull, cursor?.let(QComment.id::gt))
            .orderBy(QComment.id.asc())
            .limit(size.toLong())
            .fetch()

    /**
     * 없는 태그만 새로 만든다.
     *
     * ponytail: 같은 태그를 두 사람이 동시에 처음 쓰면 `UNQ_HASHTAG_NAME` 에 걸려 글 작성이 실패한다.
     * 태그가 처음 등장하는 순간에만 열리는 아주 좁은 창이라 재시도로 덮지 않았다.
     * 실제로 부딪히면 `@Retryable(DataIntegrityViolationException)` 을 createPost 에 건다.
     */
    override fun findOrCreateHashtags(names: Collection<String>): List<Hashtag> {
        if (names.isEmpty()) return emptyList()

        val existing = hashtags.findAllByNameIn(names.toSet())
        val missing = names.toSet() - existing.map(Hashtag::name).toSet()

        return existing + hashtags.saveAll(missing.map { Hashtag(name = it) })
    }

    override fun linkHashtags(postId: Long, hashtagIds: Collection<Long>) {
        if (hashtagIds.isEmpty()) return

        postHashtags.saveAll(hashtagIds.toSet().map { PostHashtag(postId = postId, hashtagId = it) })
    }

    /**
     * 하루치 태그별 글 수 집계.
     *
     * **멱등하다** — 더하는 게 아니라 다시 세어 덮어쓴다. 스케줄러는 락 만료·재배포로 겹쳐 돌 수 있어
     * 같은 날짜를 몇 번 돌려도 결과가 같아야 한다.
     *
     * ponytail: `searchCount` 는 건드리지 않는다. 검색 한 번마다 카운터를 올리려면 조회 경로에 쓰기가
     * 붙는데, 지금 그 수치를 쓰는 화면이 없다. 필요해지면 레디스에 모았다가 여기서 합친다.
     */
    @Transactional
    override fun aggregateDailyStats(date: LocalDate) {
        val from = date.atStartOfDay(ZoneOffset.UTC).toInstant()
        val to = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()

        val counts = dsl.select(QHashtag.name, QPost.id.count())
            .from(QPostHashtag)
            .join(QPost).on(QPost.id.eq(QPostHashtag.postId))
            .join(QHashtag).on(QHashtag.id.eq(QPostHashtag.hashtagId))
            .where(QPost.createdAt.goe(from), QPost.createdAt.lt(to), QPost.deletedAt.isNull)
            .groupBy(QHashtag.name)
            .fetch()
            .associate { it.get(QHashtag.name)!! to (it.get(QPost.id.count()) ?: 0L) }

        val saved = stats.findAllByStatDate(date).associateBy(HashtagDailyStat::hashtag)

        stats.saveAll(
            counts.map { (name, count) ->
                saved[name]?.apply { postCount = count }
                    ?: HashtagDailyStat(hashtag = name, statDate = date, postCount = count, searchCount = 0)
            }
        )
    }
}

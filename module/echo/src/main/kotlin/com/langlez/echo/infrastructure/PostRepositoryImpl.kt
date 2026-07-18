package com.langlez.echo.infrastructure

import com.langlez.echo.domain.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

interface PostJpaRepository : JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE p.authorId IN :authorIds AND p.blinded = false AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    fun findFollowingFeed(authorIds: List<Long>, cursor: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p WHERE p.authorId NOT IN :excludeAuthorIds AND p.blinded = false AND (:cursor IS NULL OR p.likeCount < :cursorLikeCount OR (p.likeCount = :cursorLikeCount AND p.id < :cursor)) ORDER BY p.likeCount DESC, p.id DESC")
    fun findRecommendedFeedWithExcludes(excludeAuthorIds: List<Long>, cursor: Long?, cursorLikeCount: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p WHERE p.blinded = false AND (:cursor IS NULL OR p.likeCount < :cursorLikeCount OR (p.likeCount = :cursorLikeCount AND p.id < :cursor)) ORDER BY p.likeCount DESC, p.id DESC")
    fun findRecommendedFeedWithoutExcludes(cursor: Long?, cursorLikeCount: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p JOIN PostHashtag ph ON p.id = ph.postId JOIN Hashtag h ON ph.hashtagId = h.id WHERE h.name = :hashtag AND p.blinded = false AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    fun findByHashtag(hashtag: String, cursor: Long?, pageable: PageRequest): List<Post>
}

interface PostMediaJpaRepository : JpaRepository<PostMedia, Long> {
    fun findByPostIdOrderBySequenceAsc(postId: Long): List<PostMedia>
    fun findByPostIdIn(postIds: List<Long>): List<PostMedia>
}

interface PostLikeJpaRepository : JpaRepository<PostLike, Long> {
    fun findByMemberIdAndPostId(memberId: Long, postId: Long): PostLike?
    fun deleteByMemberIdAndPostId(memberId: Long, postId: Long)
}

interface PostReportJpaRepository : JpaRepository<PostReport, Long> {
    fun findByReporterIdAndPostId(reporterId: Long, postId: Long): PostReport?
}

interface HashtagJpaRepository : JpaRepository<Hashtag, Long> {
    fun findByName(name: String): Hashtag?
}

interface PostHashtagJpaRepository : JpaRepository<PostHashtag, Long>

@Repository
class PostRepositoryImpl(
    private val postJpa: PostJpaRepository,
    private val postMediaJpa: PostMediaJpaRepository,
    private val postLikeJpa: PostLikeJpaRepository,
    private val postReportJpa: PostReportJpaRepository,
    private val hashtagJpa: HashtagJpaRepository,
    private val postHashtagJpa: PostHashtagJpaRepository,
) : PostRepository {

    override fun save(post: Post): Post = postJpa.save(post)

    override fun findById(id: Long): Post? = postJpa.findByIdOrNull(id)

    override fun findFollowingFeed(authorIds: List<Long>, cursor: Long?, size: Int): List<Post> {
        if (authorIds.isEmpty()) return emptyList()
        return postJpa.findFollowingFeed(authorIds, cursor, PageRequest.of(0, size))
    }

    override fun findRecommendedFeed(excludeAuthorIds: List<Long>, cursor: Long?, size: Int): List<Post> {
        val cursorLikeCount = cursor?.let { postJpa.findByIdOrNull(it)?.likeCount }
        val pageable = PageRequest.of(0, size)
        return if (excludeAuthorIds.isEmpty()) {
            postJpa.findRecommendedFeedWithoutExcludes(cursor, cursorLikeCount, pageable)
        } else {
            postJpa.findRecommendedFeedWithExcludes(excludeAuthorIds, cursor, cursorLikeCount, pageable)
        }
    }

    override fun findByHashtag(hashtag: String, cursor: Long?, size: Int): List<Post> =
        postJpa.findByHashtag(hashtag, cursor, PageRequest.of(0, size))

    override fun saveMediaAll(mediaList: List<PostMedia>): List<PostMedia> =
        postMediaJpa.saveAll(mediaList)

    override fun findMediaByPostId(postId: Long): List<PostMedia> =
        postMediaJpa.findByPostIdOrderBySequenceAsc(postId)

    override fun findMediaByPostIds(postIds: List<Long>): List<PostMedia> {
        if (postIds.isEmpty()) return emptyList()
        return postMediaJpa.findByPostIdIn(postIds)
    }

    override fun saveLike(like: PostLike): PostLike = postLikeJpa.save(like)

    override fun findLike(memberId: Long, postId: Long): PostLike? =
        postLikeJpa.findByMemberIdAndPostId(memberId, postId)

    override fun deleteLike(memberId: Long, postId: Long) =
        postLikeJpa.deleteByMemberIdAndPostId(memberId, postId)

    override fun saveReport(report: PostReport): PostReport =
        postReportJpa.save(report)

    override fun findReport(reporterId: Long, postId: Long): PostReport? =
        postReportJpa.findByReporterIdAndPostId(reporterId, postId)

    override fun findHashtagByName(name: String): Hashtag? =
        hashtagJpa.findByName(name)

    override fun saveHashtag(hashtag: Hashtag): Hashtag =
        hashtagJpa.save(hashtag)

    override fun savePostHashtag(postHashtag: PostHashtag): PostHashtag =
        postHashtagJpa.save(postHashtag)
}

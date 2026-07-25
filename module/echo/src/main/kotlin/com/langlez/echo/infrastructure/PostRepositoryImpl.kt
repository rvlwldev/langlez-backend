package com.langlez.echo.infrastructure

import com.langlez.echo.domain.*
import com.langlez.echo.infrastructure.jpa.*
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class PostRepositoryImpl(
    private val postJpa: PostJpaRepository,
    private val postMediaJpa: PostMediaJpaRepository,
    private val postLikeJpa: PostLikeJpaRepository,
    private val hashtagJpa: HashtagJpaRepository,
    private val postHashtagJpa: PostHashtagJpaRepository,
) : PostRepository {

    override fun save(post: Post): Post = postJpa.save(post)

    override fun findById(id: Long): Post? = postJpa.findByIdOrNull(id)?.takeIf { it.deletedAt == null }

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

    override fun findAllForAdmin(cursor: Long?, size: Int): List<Post> =
        postJpa.findAllForAdmin(cursor, PageRequest.of(0, size))

    override fun saveMediaAll(mediaList: List<PostMedia>): List<PostMedia> =
        postMediaJpa.saveAll(mediaList)

    override fun findMediaByPostIds(postIds: List<Long>): List<PostMedia> {
        if (postIds.isEmpty()) return emptyList()
        return postMediaJpa.findByPostIdIn(postIds)
    }

    override fun saveLike(like: PostLike): PostLike = postLikeJpa.save(like)

    override fun findLike(memberId: Long, postId: Long): PostLike? =
        postLikeJpa.findByMemberIdAndPostId(memberId, postId)

    override fun deleteLike(memberId: Long, postId: Long) =
        postLikeJpa.deleteByMemberIdAndPostId(memberId, postId)

    override fun incrementLikeCount(postId: Long) {
        postJpa.incrementLikeCount(postId)
    }

    override fun decrementLikeCount(postId: Long) {
        postJpa.decrementLikeCount(postId)
    }

    override fun incrementReportCount(postId: Long) {
        postJpa.incrementReportCount(postId)
    }

    override fun blindIfThresholdReached(postId: Long, threshold: Int) {
        postJpa.blindIfThresholdReached(postId, threshold)
    }

    override fun findHashtagByName(name: String): Hashtag? =
        hashtagJpa.findByName(name)

    override fun findHashtagsByNames(names: Collection<String>): List<Hashtag> =
        if (names.isEmpty()) emptyList() else hashtagJpa.findByNameIn(names)

    override fun saveHashtag(hashtag: Hashtag): Hashtag =
        hashtagJpa.save(hashtag)

    override fun saveHashtagsAll(hashtags: List<Hashtag>): List<Hashtag> =
        if (hashtags.isEmpty()) emptyList() else hashtagJpa.saveAll(hashtags)

    override fun savePostHashtag(postHashtag: PostHashtag): PostHashtag =
        postHashtagJpa.save(postHashtag)

    override fun savePostHashtagsAll(postHashtags: List<PostHashtag>): List<PostHashtag> =
        if (postHashtags.isEmpty()) emptyList() else postHashtagJpa.saveAll(postHashtags)
}

package com.langlez.echo.application

import com.langlez.core.LanglezException
import com.langlez.echo.api.EchoResponse
import com.langlez.echo.domain.*
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional
class EchoService(
    private val postRepository: PostRepository,
    private val memberRepo: MemberRepository,
    private val relationshipRepository: RelationshipRepository,
    private val hashtagTrendRepository: HashtagTrendRepository,
) {

    fun createPost(
        authorId: Long,
        content: String,
        media: List<Pair<String, PostMedia.Type>>
    ): Post {
        if (content.length > Post.MAX_CONTENT_LENGTH) {
            throw LanglezException(400, "echo.content.too-long")
        }
        if (media.size > Post.MAX_MEDIA_COUNT) {
            throw LanglezException(400, "echo.media.too-many")
        }

        val post = postRepository.save(Post(authorId = authorId, content = content))

        if (media.isNotEmpty()) {
            val mediaEntities = media.mapIndexed { index, pair ->
                PostMedia(
                    postId = post.id,
                    url = pair.first,
                    type = pair.second,
                    sequence = index
                )
            }
            postRepository.saveMediaAll(mediaEntities)
        }

        val regex = Regex("#([가-힣a-zA-Z0-9_]+)")
        val tags = regex.findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .map { name ->
                val tag = postRepository.findHashtagByName(name) ?: postRepository.saveHashtag(Hashtag(name = name))
                hashtagTrendRepository.recordPostUsage(name)
                tag
            }
            .toList()

        tags.forEach { tag ->
            postRepository.savePostHashtag(PostHashtag(postId = post.id, hashtagId = tag.id))
        }

        return post
    }

    fun likePost(memberId: Long, postId: Long) {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (postRepository.findLike(memberId, postId) != null) return

        postRepository.saveLike(PostLike(postId = postId, memberId = memberId))
        post.increaseLikeCount()
        postRepository.save(post)
    }

    fun unlikePost(memberId: Long, postId: Long) {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        val like = postRepository.findLike(memberId, postId) ?: return

        postRepository.deleteLike(memberId, postId)
        post.decreaseLikeCount()
        postRepository.save(post)
    }

    fun reportPost(reporterId: Long, postId: Long, reason: String) {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (postRepository.findReport(reporterId, postId) != null) {
            throw LanglezException(400, "echo.report.already-reported")
        }

        postRepository.saveReport(PostReport(postId = postId, reporterId = reporterId, reason = reason))
        post.increaseReportCount()
        postRepository.save(post)
    }

    @Transactional(readOnly = true)
    fun getFollowingFeed(memberId: Long, cursor: Long?, size: Int): EchoResponse.CursorList {
        val follows = relationshipRepository.findFollowings(memberId, null, 1000)
        val followedIds = follows.map { it.followedId }
        val posts = postRepository.findFollowingFeed(followedIds, cursor, size)
        return buildCursorList(posts, posts.size == size, posts.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun getRecommendedFeed(memberId: Long, cursor: Long?, size: Int): EchoResponse.CursorList {
        val follows = relationshipRepository.findFollowings(memberId, null, 1000)
        val excludeAuthorIds = listOf(memberId) + follows.map { it.followedId }
        val posts = postRepository.findRecommendedFeed(excludeAuthorIds, cursor, size)
        return buildCursorList(posts, posts.size == size, posts.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun searchByHashtag(tag: String, cursor: Long?, size: Int): EchoResponse.CursorList {
        val posts = postRepository.findByHashtag(tag, cursor, size)
        hashtagTrendRepository.recordSearch(tag)
        return buildCursorList(posts, posts.size == size, posts.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun getTrendingHashtags(days: Int, limit: Int): List<EchoResponse.TrendingHashtag> {
        if (days != 1 && days != 7 && days != 30) {
            throw LanglezException(400, "echo.trending.invalid-days")
        }
        val trending = hashtagTrendRepository.getTrending(days, limit)
        return trending.map { EchoResponse.TrendingHashtag(it.hashtag, it.count) }
    }

    private fun buildCursorList(
        posts: List<Post>,
        hasMore: Boolean,
        lastEntityId: Long?
    ): EchoResponse.CursorList {
        val authorIds = posts.map { it.authorId }.distinct()
        val members = memberRepo.findByIds(authorIds)

        val postIds = posts.map { it.id }
        val mediaList = postRepository.findMediaByPostIds(postIds)
        val mediaMap = mediaList.groupBy { it.postId }

        val postDtos = posts.map { post ->
            val author = members.find { it.id == post.authorId }
            val username = author?.username ?: "unknown"
            val nickname = author?.nickname ?: "Unknown"

            val postMediaDtos = (mediaMap[post.id] ?: emptyList())
                .sortedBy { it.sequence }
                .map { EchoResponse.PostMediaDto(it.url, it.type) }

            EchoResponse.PostDto(
                postId = post.id,
                username = username,
                nickname = nickname,
                content = post.content,
                media = postMediaDtos,
                likeCount = post.likeCount,
                createdAt = post.createdAt
            )
        }

        val nextCursor = if (hasMore) lastEntityId else null
        return EchoResponse.CursorList(nextCursor, postDtos)
    }
}

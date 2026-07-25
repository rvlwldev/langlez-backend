package com.langlez.echo.application

import com.langlez.core.LanglezException
import com.langlez.echo.api.EchoResponse
import com.langlez.echo.domain.*
import com.langlez.echo.infrastructure.outbox.EchoOutBoxRepository
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.redis.ratelimit.DailyRateLimiter
import com.langlez.relationship.domain.RelationshipRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
@Transactional
class EchoService(
    private val postRepository: PostRepository,
    private val memberRepository: MemberRepository,
    private val relationshipRepository: RelationshipRepository,
    private val hashtagTrendRepository: HashtagTrendRepository,
    private val dailyRateLimiter: DailyRateLimiter,
    private val commentRepository: CommentRepository,
    private val echoOutBoxRepository: EchoOutBoxRepository,
    private val redissonClient: RedissonClient,
) {

    fun createPost(
        authorId: Long,
        content: String,
        media: List<Pair<String, PostMedia.Type>>
    ): EchoResponse.PostDto {
        if (content.length > Post.MAX_CONTENT_LENGTH) {
            throw LanglezException(400, "echo.content.too-long")
        }
        if (media.size > Post.MAX_MEDIA_COUNT) {
            throw LanglezException(400, "echo.media.too-many")
        }

        val author = memberRepository.findById(authorId) ?: throw LanglezException(404, "member.not-found")
        if (author.role == Member.Role.MEMBER) {
            if (!dailyRateLimiter.tryConsume("echo:post:$authorId", 1)) {
                throw LanglezException(429, "echo.post.daily-limit-exceeded")
            }
        }

        val post = postRepository.save(Post(authorId = authorId, content = content))

        val mediaEntities = if (media.isNotEmpty()) {
            val entities = media.mapIndexed { index, pair ->
                PostMedia(
                    postId = post.id,
                    url = pair.first,
                    type = pair.second,
                    sequence = index
                )
            }
            postRepository.saveMediaAll(entities).also { saved ->
                echoOutBoxRepository.save(
                    aggregateType = "ECHO_POST",
                    aggregateId = post.id.toString(),
                    eventName = "echo-attachments-uploaded",
                    payload = EchoAttachmentsUploadedEvent(
                        postId = post.id.toString(),
                        uploaderId = authorId,
                        attachments = saved.map {
                            EchoAttachmentsUploadedEvent.Item(storageKey = it.url, fileType = it.type.name)
                        },
                    ),
                )
            }
        } else {
            emptyList()
        }

        val regex = Regex("#([가-힣a-zA-Z0-9_]+)")
        val tagNames = regex.findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .toList()

        if (tagNames.isNotEmpty()) {
            val existingMap = postRepository.findHashtagsByNames(tagNames)
                .associateBy { it.name }
                .toMutableMap()
            val missingNames = tagNames.filter { !existingMap.containsKey(it) }

            if (missingNames.isNotEmpty()) {
                val newHashtags = missingNames.map { Hashtag(name = it) }
                try {
                    val savedNew = postRepository.saveHashtagsAll(newHashtags)
                    savedNew.forEach { existingMap[it.name] = it }
                } catch (e: Exception) {
                    missingNames.forEach { name ->
                        if (!existingMap.containsKey(name)) {
                            val tag = try {
                                postRepository.saveHashtag(Hashtag(name = name))
                            } catch (ex: Exception) {
                                postRepository.findHashtagByName(name) ?: throw ex
                            }
                            existingMap[name] = tag
                        }
                    }
                }
            }

            val tags = tagNames.mapNotNull { existingMap[it] }
            val postHashtags = tags.map { PostHashtag(postId = post.id, hashtagId = it.id) }
            postRepository.savePostHashtagsAll(postHashtags)

            runAfterCommit {
                tagNames.forEach { name ->
                    hashtagTrendRepository.recordPostUsage(name)
                }
            }
        }

        return EchoResponse.PostDto(
            postId = post.id,
            username = author.username,
            nickname = author.nickname,
            content = post.content,
            media = mediaEntities.map { EchoResponse.PostMediaDto(it.url, it.type) },
            likeCount = post.likeCount,
            createdAt = post.createdAt
        )
    }

    fun likePost(memberId: Long, postId: Long) {
        postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (postRepository.findLike(memberId, postId) != null) return

        try {
            postRepository.saveLike(PostLike(postId = postId, memberId = memberId))
            postRepository.incrementLikeCount(postId)
        } catch (e: Exception) {
            // UNQ_POST_LIKE violation absorbed for concurrent requests
        }
    }

    fun unlikePost(memberId: Long, postId: Long) {
        postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        postRepository.findLike(memberId, postId) ?: return

        postRepository.deleteLike(memberId, postId)
        postRepository.decrementLikeCount(postId)
    }

    fun deletePost(memberId: Long, postId: Long) {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (post.authorId != memberId) {
            throw LanglezException(403, "echo.not-author")
        }
        post.delete()
        postRepository.save(post)
    }

    fun deleteComment(memberId: Long, postId: Long, commentId: Long) {
        postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        val comment = commentRepository.findById(commentId) ?: throw LanglezException(404, "echo.comment.not-found")
        if (comment.postId != postId) {
            throw LanglezException(404, "echo.comment.not-found")
        }
        if (comment.authorId != memberId) {
            throw LanglezException(403, "echo.not-author")
        }
        comment.delete()
        commentRepository.save(comment)
    }

    fun reportPost(reporterId: Long, postId: Long, reason: String) {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (post.authorId == reporterId) {
            throw LanglezException(400, "echo.report.cannot-report-own-post")
        }

        // 체크 후 별도 저장이면 동시 요청이 둘 다 통과할 수 있으므로, trySet 자체를 원자적 게이트로 사용한다.
        val bucket = redissonClient.getBucket<String>("echo:report:$postId:$reporterId")
        if (!bucket.trySet("1", 30, TimeUnit.DAYS)) {
            throw LanglezException(400, "echo.report.already-reported")
        }

        postRepository.incrementReportCount(postId)
        postRepository.blindIfThresholdReached(postId, Post.BLIND_THRESHOLD)

        echoOutBoxRepository.save(
            aggregateType = "ECHO_REPORT",
            aggregateId = postId.toString(),
            eventName = "echo-post-reported",
            payload = EchoPostReportedEvent(
                postId = postId.toString(),
                reporterId = reporterId,
                reportedUserId = post.authorId,
                reason = reason,
            ),
        )
    }

    @Transactional(readOnly = true)
    fun getFollowingFeed(memberId: Long, cursor: Long?, size: Int): EchoResponse.CursorList {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val follows = relationshipRepository.findFollowings(memberId, null, MAX_FOLLOWINGS_FETCH_SIZE)
        val followedIds = follows.map { it.followedId }
        val posts = postRepository.findFollowingFeed(followedIds, cursor, boundedSize)
        return buildCursorList(posts, posts.size == boundedSize, posts.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun getRecommendedFeed(memberId: Long, cursor: Long?, size: Int): EchoResponse.CursorList {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val follows = relationshipRepository.findFollowings(memberId, null, MAX_FOLLOWINGS_FETCH_SIZE)
        val excludeAuthorIds = listOf(memberId) + follows.map { it.followedId }
        val posts = postRepository.findRecommendedFeed(excludeAuthorIds, cursor, boundedSize)
        return buildCursorList(posts, posts.size == boundedSize, posts.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun searchByHashtag(tag: String, cursor: Long?, size: Int): EchoResponse.CursorList {
        val boundedSize = size.coerceIn(1, MAX_PAGE_SIZE)
        val posts = postRepository.findByHashtag(tag, cursor, boundedSize)
        runAfterCommit {
            hashtagTrendRepository.recordSearch(tag)
        }
        return buildCursorList(posts, posts.size == boundedSize, posts.lastOrNull()?.id)
    }

    @Transactional(readOnly = true)
    fun getTrendingHashtags(days: Int, limit: Int): List<EchoResponse.TrendingHashtag> {
        val boundedDays = days.coerceIn(1, MAX_TRENDING_DAYS)
        val boundedLimit = limit.coerceIn(1, MAX_TRENDING_LIMIT)
        val trending = hashtagTrendRepository.getTrending(boundedDays, boundedLimit)
        return trending.map { EchoResponse.TrendingHashtag(it.hashtag, it.count) }
    }

    private fun buildCursorList(
        posts: List<Post>,
        hasMore: Boolean,
        lastEntityId: Long?
    ): EchoResponse.CursorList {
        val authorIds = posts.map { it.authorId }.distinct()
        val memberMap = memberRepository.findByIds(authorIds).associateBy { it.id }

        val postIds = posts.map { it.id }
        val mediaList = postRepository.findMediaByPostIds(postIds)
        val mediaMap = mediaList.groupBy { it.postId }

        val postDtos = posts.map { post ->
            val author = memberMap[post.authorId]
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

    fun addComment(authorId: Long, postId: Long, content: String): EchoResponse.CommentDto {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (post.blinded) {
            throw LanglezException(404, "echo.post.not-found")
        }
        if (content.length > Comment.MAX_CONTENT_LENGTH) {
            throw LanglezException(400, "echo.comment.too-long")
        }
        val author = memberRepository.findById(authorId) ?: throw LanglezException(404, "member.not-found")
        val comment = commentRepository.save(Comment(postId = postId, authorId = authorId, content = content))
        return EchoResponse.CommentDto(
            commentId = comment.id,
            username = author.username,
            nickname = author.nickname,
            content = comment.content,
            createdAt = comment.createdAt
        )
    }

    @Transactional(readOnly = true)
    fun getComments(postId: Long, cursor: Long?, size: Int): EchoResponse.CommentCursorList {
        val post = postRepository.findById(postId) ?: throw LanglezException(404, "echo.post.not-found")
        if (post.blinded) {
            throw LanglezException(404, "echo.post.not-found")
        }
        val boundedSize = size.coerceIn(1, 100)
        val comments = commentRepository.findByPost(postId, cursor, boundedSize)
        val authorIds = comments.map { it.authorId }.distinct()
        val memberMap = memberRepository.findByIds(authorIds).associateBy { it.id }

        val commentDtos = comments.map { comment ->
            val author = memberMap[comment.authorId]
            val username = author?.username ?: "unknown"
            val nickname = author?.nickname ?: "Unknown"

            EchoResponse.CommentDto(
                commentId = comment.id,
                username = username,
                nickname = nickname,
                content = comment.content,
                createdAt = comment.createdAt
            )
        }

        val nextCursor = if (comments.size == boundedSize) comments.lastOrNull()?.id else null
        return EchoResponse.CommentCursorList(nextCursor, commentDtos)
    }

    private fun runAfterCommit(action: () -> Unit) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    action()
                }
            })
        } else {
            action()
        }
    }

    companion object {
        private const val MAX_PAGE_SIZE = 100
        private const val MAX_TRENDING_LIMIT = 50
        private const val MAX_TRENDING_DAYS = 31
        private const val MAX_FOLLOWINGS_FETCH_SIZE = 1000
    }
}

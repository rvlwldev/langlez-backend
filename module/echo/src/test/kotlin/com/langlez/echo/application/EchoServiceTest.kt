package com.langlez.echo.application

import com.langlez.core.LanglezException
import com.langlez.echo.api.EchoResponse
import com.langlez.echo.domain.*
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.redis.ratelimit.DailyRateLimiter
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import org.redisson.api.RBucket
import org.redisson.api.RedissonClient
import java.time.Duration
import java.util.concurrent.TimeUnit

class EchoServiceTest : BehaviorSpec({

    val postRepository = mockk<PostRepository>()
    val memberRepo = mockk<MemberRepository>()
    val relationshipRepository = mockk<RelationshipRepository>()
    val hashtagTrendRepository = mockk<HashtagTrendRepository>(relaxed = true)
    val dailyRateLimiter = mockk<DailyRateLimiter>()
    val commentRepository = mockk<CommentRepository>()
    val echoOutBoxRepository = mockk<com.langlez.echo.infrastructure.outbox.EchoOutBoxRepository>(relaxed = true)
    val redissonClient = mockk<RedissonClient>()

    // OutBoxRepository가 제네릭 인터페이스라 relaxed mock이 반환 타입을 상위 타입(AbstractOutBox)으로
    // 추론해 ClassCastException이 난다. 구체 타입을 명시적으로 stub 해 준다.
    every { echoOutBoxRepository.save(any(), any(), any(), any()) } returns
        com.langlez.echo.infrastructure.outbox.EchoOutBox("ECHO", "0", "stub", "{}")

    val service = EchoService(
        postRepository,
        memberRepo,
        relationshipRepository,
        hashtagTrendRepository,
        dailyRateLimiter,
        commentRepository,
        echoOutBoxRepository,
        redissonClient,
    )

    Given("게시물 작성 시") {
        val authorId = 1L
        val content = "오늘 날씨 정말 좋네요! #날씨 #주말"
        val media = listOf("http://s3.com/image1.png" to PostMedia.Type.IMAGE)

        val authorMember = mockk<Member>()
        every { authorMember.id } returns authorId
        every { authorMember.role } returns Member.Role.MEMBER
        every { authorMember.username } returns "test_user"
        every { authorMember.nickname } returns "Test"
        every { memberRepo.findById(authorId) } returns authorMember
        every { dailyRateLimiter.tryConsume(any(), any()) } returns true

        every { postRepository.save(any()) } answers {
            val post = firstArg<Post>()
            Post(id = 100L, authorId = post.authorId, content = post.content, createdAt = post.createdAt)
        }
        every { postRepository.saveMediaAll(any()) } answers { firstArg() }
        every { postRepository.findHashtagsByNames(any()) } returns emptyList()
        every { postRepository.saveHashtagsAll(any()) } answers { firstArg() }
        every { postRepository.saveHashtag(any()) } answers {
            val hashtag = firstArg<Hashtag>()
            Hashtag(id = 50L, name = hashtag.name, createdAt = hashtag.createdAt)
        }
        every { postRepository.savePostHashtagsAll(any()) } answers { firstArg() }
        every { postRepository.savePostHashtag(any()) } answers { firstArg() }

        When("정상적인 본문과 미디어를 전달하면") {
            val result = service.createPost(authorId, content, media)

            Then("게시물이 정상적으로 저장되어야 한다") {
                result shouldNotBe null
                result.postId shouldBe 100L
                result.content shouldBe content
                result.username shouldBe "test_user"
                result.nickname shouldBe "Test"
                verify(exactly = 1) { postRepository.save(any()) }
                verify(exactly = 1) { postRepository.saveMediaAll(any()) }
            }

            Then("해쉬태그가 파싱되어 저장되고 사용이 기록되어야 한다") {
                verify(exactly = 1) { postRepository.saveHashtagsAll(any()) }
                verify(exactly = 1) { postRepository.savePostHashtagsAll(any()) }
                verify { hashtagTrendRepository.recordPostUsage("날씨") }
                verify { hashtagTrendRepository.recordPostUsage("주말") }
            }
        }

        When("1000자를 초과하는 본문을 작성하면") {
            val longContent = "A".repeat(1001)

            Then("예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.createPost(authorId, longContent, media)
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.content.too-long"
            }
        }

        When("12개를 초과하는 미디어를 전달하면") {
            val tooManyMedia = (1..13).map { "http://s3.com/image$it.png" to PostMedia.Type.IMAGE }

            Then("예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.createPost(authorId, content, tooManyMedia)
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.media.too-many"
            }
        }

        When("MEMBER 역할이 하루 제한(1회)을 초과하면") {
            every { dailyRateLimiter.tryConsume("echo:post:$authorId", 1) } returns false

            Then("429 LanglezException이 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.createPost(authorId, content, media)
                }
                ex.status shouldBe 429
                ex.message shouldBe "echo.post.daily-limit-exceeded"
            }
        }

        When("PREMIUM 역할인 유저가 작성하면") {
            val premiumAuthorId = 2L
            val premiumMember = mockk<Member>()
            every { premiumMember.id } returns premiumAuthorId
            every { premiumMember.role } returns Member.Role.PREMIUM
            every { premiumMember.username } returns "premium_user"
            every { premiumMember.nickname } returns "Premium"
            every { memberRepo.findById(premiumAuthorId) } returns premiumMember

            service.createPost(premiumAuthorId, content, media)

            Then("rate limit 체크를 건너뛰고 정상 저장되어야 한다") {
                verify(exactly = 0) { dailyRateLimiter.tryConsume("echo:post:$premiumAuthorId", any()) }
            }
        }
    }

    Given("게시물 좋아요/취소 시") {
        val memberId = 1L
        val postId = 100L
        val post = Post(id = postId, authorId = 2L, content = "테스트 본문")

        every { postRepository.findById(postId) } returns post
        every { postRepository.incrementLikeCount(postId) } returns Unit
        every { postRepository.decrementLikeCount(postId) } returns Unit

        When("처음 좋아요를 누르면") {
            every { postRepository.findLike(memberId, postId) } returns null
            every { postRepository.saveLike(any()) } answers { firstArg() }

            service.likePost(memberId, postId)

            Then("좋아요 정보가 저장되고 카운트가 증가해야 한다") {
                verify(exactly = 1) { postRepository.saveLike(any()) }
                verify(exactly = 1) { postRepository.incrementLikeCount(postId) }
            }
        }

        When("이미 누른 상태에서 다시 좋아요를 누르면") {
            every { postRepository.findLike(memberId, postId) } returns PostLike(postId = postId, memberId = memberId)

            service.likePost(memberId, postId)

            Then("추가 저장 없이 무시된다") {
                verify(exactly = 1) { postRepository.saveLike(any()) }
                verify(exactly = 1) { postRepository.incrementLikeCount(postId) }
            }
        }

        When("좋아요를 취소하면") {
            every { postRepository.findLike(memberId, postId) } returns PostLike(postId = postId, memberId = memberId)
            every { postRepository.deleteLike(memberId, postId) } returns Unit

            service.unlikePost(memberId, postId)

            Then("좋아요 정보가 제거되고 카운트가 감소해야 한다") {
                verify(exactly = 1) { postRepository.deleteLike(memberId, postId) }
                verify(exactly = 1) { postRepository.decrementLikeCount(postId) }
            }
        }
    }

    Given("게시물 삭제 시") {
        val postId = 100L
        val authorId = 1L
        val otherMemberId = 2L
        val post = Post(id = postId, authorId = authorId, content = "삭제 대상 글")

        every { postRepository.save(any()) } answers { firstArg() }

        When("작성자 본인이 삭제를 요청하면") {
            every { postRepository.findById(postId) } returns post

            service.deletePost(authorId, postId)

            Then("deletedAt 필드가 설정되고 저장되어야 한다") {
                post.deletedAt shouldNotBe null
                verify { postRepository.save(post) }
            }
        }

        When("작성자가 아닌 유저가 삭제를 요청하면") {
            every { postRepository.findById(postId) } returns post

            Then("403 예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deletePost(otherMemberId, postId)
                }
                ex.status shouldBe 403
                ex.message shouldBe "echo.not-author"
            }
        }

        When("존재하지 않는 게시물 삭제를 요청하면") {
            every { postRepository.findById(999L) } returns null

            Then("404 예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deletePost(authorId, 999L)
                }
                ex.status shouldBe 404
                ex.message shouldBe "echo.post.not-found"
            }
        }
    }

    Given("댓글 삭제 시") {
        val postId = 100L
        val commentId = 200L
        val authorId = 1L
        val otherMemberId = 2L
        val post = Post(id = postId, authorId = 10L, content = "게시글")
        val comment = Comment(id = commentId, postId = postId, authorId = authorId, content = "삭제 대상 댓글")

        every { commentRepository.save(any()) } answers { firstArg() }

        When("작성자 본인이 댓글 삭제를 요청하면") {
            every { postRepository.findById(postId) } returns post
            every { commentRepository.findById(commentId) } returns comment

            service.deleteComment(authorId, postId, commentId)

            Then("deletedAt 필드가 설정되고 저장되어야 한다") {
                comment.deletedAt shouldNotBe null
                verify { commentRepository.save(comment) }
            }
        }

        When("작성자가 아닌 유저가 댓글 삭제를 요청하면") {
            every { postRepository.findById(postId) } returns post
            every { commentRepository.findById(commentId) } returns comment

            Then("403 예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteComment(otherMemberId, postId, commentId)
                }
                ex.status shouldBe 403
                ex.message shouldBe "echo.not-author"
            }
        }

        When("존재하지 않는 댓글 삭제를 요청하면") {
            every { postRepository.findById(postId) } returns post
            every { commentRepository.findById(999L) } returns null

            Then("404 예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteComment(authorId, postId, 999L)
                }
                ex.status shouldBe 404
                ex.message shouldBe "echo.comment.not-found"
            }
        }
    }

    Given("게시물 신고 시") {
        val postId = 100L
        val post = Post(id = postId, authorId = 10L, content = "신고 대상 글")
        val bucket = mockk<RBucket<String>>(relaxed = true)

        every { postRepository.findById(postId) } returns post
        every { redissonClient.getBucket<String>(any<String>()) } returns bucket
        every { postRepository.incrementReportCount(postId) } returns Unit
        every { postRepository.blindIfThresholdReached(postId, Post.BLIND_THRESHOLD) } returns Unit

        When("자기 글을 신고하려고 하면") {
            Then("400 예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.reportPost(10L, postId, "spam")
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.report.cannot-report-own-post"
            }
        }

        When("정상 신고 시") {
            every { bucket.isExists } returns false
            every { bucket.trySet("1", any<Long>(), any<TimeUnit>()) } returns true

            service.reportPost(1L, postId, "spam")

            Then("outbox 이벤트 저장 및 레디스 키 세팅이 수행되어야 한다") {
                verify(exactly = 1) { bucket.trySet("1", any<Long>(), any<TimeUnit>()) }
                verify(exactly = 1) { postRepository.incrementReportCount(postId) }
                verify(exactly = 1) { postRepository.blindIfThresholdReached(postId, Post.BLIND_THRESHOLD) }
                verify(exactly = 1) {
                    echoOutBoxRepository.save(
                        aggregateType = "ECHO_REPORT",
                        aggregateId = postId.toString(),
                        eventName = "echo-post-reported",
                        payload = EchoPostReportedEvent(
                            postId = postId.toString(),
                            reporterId = 1L,
                            reportedUserId = 10L,
                            reason = "spam"
                        )
                    )
                }
            }
        }

        When("동일 유저가 이미 신고한 게시물을 다시 신고하면") {
            every { bucket.trySet("1", any<Long>(), any<TimeUnit>()) } returns false

            Then("400 예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.reportPost(1L, postId, "spam")
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.report.already-reported"
            }
        }
    }

    Given("피드 조회 시") {
        val memberId = 1L
        val followingId = 2L
        val post = Post(id = 10L, authorId = followingId, content = "팔로잉 글")

        every { relationshipRepository.findFollowings(memberId, null, 1000) } returns listOf(Follow(memberId, followingId))
        every { postRepository.findFollowingFeed(listOf(followingId), null, 20) } returns listOf(post)
        every { postRepository.findRecommendedFeed(any(), null, 20) } returns listOf(post)
        every { postRepository.findMediaByPostIds(any()) } returns emptyList()

        val authorMember = mockk<Member>()
        every { authorMember.id } returns followingId
        every { authorMember.username } returns "follower_user"
        every { authorMember.nickname } returns "Follower"
        every { memberRepo.findByIds(listOf(followingId)) } returns listOf(authorMember)

        When("팔로잉 피드를 요청하면") {
            val result = service.getFollowingFeed(memberId, null, 20)

            Then("팔로우한 작성자의 글들이 반환되어야 한다") {
                result.posts.size shouldBe 1
                result.posts[0].username shouldBe "follower_user"
                result.posts[0].nickname shouldBe "Follower"
            }
        }

        When("추천 피드를 요청하면") {
            val result = service.getRecommendedFeed(memberId, null, 20)

            Then("본인과 팔로잉이 제외된 글들이 반환되어야 한다") {
                result.posts.size shouldBe 1
                verify { postRepository.findRecommendedFeed(listOf(memberId, followingId), null, 20) }
            }
        }
    }

    Given("피드 조회 시 size가 상한을 초과하면") {
        val memberId = 1L
        val followingId = 2L
        val post = Post(id = 10L, authorId = followingId, content = "테스트 글")

        every { relationshipRepository.findFollowings(memberId, null, 1000) } returns listOf(Follow(memberId, followingId))
        every { postRepository.findFollowingFeed(listOf(followingId), null, 100) } returns listOf(post)
        every { postRepository.findRecommendedFeed(any(), null, 100) } returns listOf(post)
        every { postRepository.findByHashtag(any(), null, 100) } returns listOf(post)
        every { postRepository.findMediaByPostIds(any()) } returns emptyList()

        val authorMember = mockk<Member>()
        every { authorMember.id } returns followingId
        every { authorMember.username } returns "test_user"
        every { authorMember.nickname } returns "Test"
        every { memberRepo.findByIds(any()) } returns listOf(authorMember)

        When("getFollowingFeed를 size 9999로 호출하면") {
            service.getFollowingFeed(memberId, null, 9999)

            Then("실제 repository는 MAX_PAGE_SIZE인 100으로 호출되어야 한다") {
                verify { postRepository.findFollowingFeed(listOf(followingId), null, 100) }
            }
        }

        When("getRecommendedFeed를 size 9999로 호출하면") {
            service.getRecommendedFeed(memberId, null, 9999)

            Then("실제 repository는 MAX_PAGE_SIZE인 100으로 호출되어야 한다") {
                verify { postRepository.findRecommendedFeed(listOf(memberId, followingId), null, 100) }
            }
        }

        When("searchByHashtag를 size 9999로 호출하면") {
            service.searchByHashtag("tag", null, 9999)

            Then("실제 repository는 MAX_PAGE_SIZE인 100으로 호출되어야 한다") {
                verify { postRepository.findByHashtag("tag", null, 100) }
            }
        }
    }

    Given("인기 해시태그 조회 시") {
        val trendingHashtags = listOf(HashtagTrendCount("kotlin", 5))
        every { hashtagTrendRepository.getTrending(7, any()) } returns trendingHashtags
        every { hashtagTrendRepository.getTrending(7, 50) } returns trendingHashtags

        When("days가 범위 내 임의의 값(예: 5)이면") {
            every { hashtagTrendRepository.getTrending(5, 10) } returns trendingHashtags
            val result = service.getTrendingHashtags(5, 10)

            Then("정상적으로 hashtagTrendRepository.getTrending이 5일 범위로 호출된다") {
                result.size shouldBe 1
                verify { hashtagTrendRepository.getTrending(5, 10) }
            }
        }

        When("days가 유효한 값(예: 7)이면") {
            val result = service.getTrendingHashtags(7, 10)

            Then("정상적으로 hashtagTrendRepository.getTrending이 호출되고 결과가 반환된다") {
                result.size shouldBe 1
                result[0].hashtag shouldBe "kotlin"
                result[0].count shouldBe 5
                verify { hashtagTrendRepository.getTrending(7, 10) }
            }
        }

        When("limit을 상한(50)보다 크게(예: 9999) 요청하면") {
            service.getTrendingHashtags(7, 9999)

            Then("hashtagTrendRepository.getTrending이 실제로는 limit=50으로 호출된다") {
                verify { hashtagTrendRepository.getTrending(7, 50) }
            }
        }
    }

    Given("댓글 작성 및 조회 시") {
        clearMocks(commentRepository)
        val authorId = 1L
        val postId = 100L
        val post = Post(id = postId, authorId = 2L, content = "게시글")

        every { postRepository.findById(postId) } returns post
        every { postRepository.findById(999L) } returns null

        val authorMember = mockk<Member>()
        every { authorMember.id } returns authorId
        every { authorMember.username } returns "commenter"
        every { authorMember.nickname } returns "CommenterNick"
        every { memberRepo.findById(authorId) } returns authorMember

        every { commentRepository.save(any()) } answers {
            val comment = firstArg<Comment>()
            Comment(id = 200L, postId = comment.postId, authorId = comment.authorId, content = comment.content, createdAt = comment.createdAt)
        }

        When("정상적으로 댓글을 작성하면") {
            val result = service.addComment(authorId, postId, "댓글 내용")

            Then("댓글이 저장되고 저장된 댓글이 반환된다") {
                result shouldNotBe null
                result.commentId shouldBe 200L
                result.content shouldBe "댓글 내용"
                result.username shouldBe "commenter"
                result.nickname shouldBe "CommenterNick"
                verify(exactly = 1) { commentRepository.save(any()) }
            }
        }

        When("존재하지 않는 게시물에 댓글을 작성하면") {
            Then("404 에러가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.addComment(authorId, 999L, "댓글 내용")
                }
                ex.status shouldBe 404
                ex.message shouldBe "echo.post.not-found"
            }
        }

        When("500자를 초과하는 댓글을 작성하면") {
            val longContent = "A".repeat(501)

            Then("400 에러가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.addComment(authorId, postId, longContent)
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.comment.too-long"
            }
        }

        When("댓글 목록을 조회하면") {
            val comment = Comment(id = 200L, postId = postId, authorId = authorId, content = "댓글 내용")
            every { commentRepository.findByPost(postId, null, 20) } returns listOf(comment)

            val authorMember2 = mockk<Member>()
            every { authorMember2.id } returns authorId
            every { authorMember2.username } returns "commenter"
            every { authorMember2.nickname } returns "CommenterNick"
            every { memberRepo.findByIds(listOf(authorId)) } returns listOf(authorMember2)

            val result = service.getComments(postId, null, 20)

            Then("작성자의 username/nickname 매핑을 확인한다") {
                result.comments.size shouldBe 1
                result.comments[0].commentId shouldBe 200L
                result.comments[0].username shouldBe "commenter"
                result.comments[0].nickname shouldBe "CommenterNick"
                result.comments[0].content shouldBe "댓글 내용"
            }
        }

        When("블라인드된 게시글에 댓글을 작성하려고 하면") {
            val blindedPost = Post(id = 300L, authorId = 2L, content = "블라인드 게시글")
            val field = Post::class.java.getDeclaredField("blinded")
            field.isAccessible = true
            field.set(blindedPost, true)

            every { postRepository.findById(300L) } returns blindedPost

            Then("404 에러가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.addComment(authorId, 300L, "댓글 내용")
                }
                ex.status shouldBe 404
                ex.message shouldBe "echo.post.not-found"
            }
        }

        When("블라인드된 게시글의 댓글을 조회하려고 하면") {
            val blindedPost = Post(id = 300L, authorId = 2L, content = "블라인드 게시글")
            val field = Post::class.java.getDeclaredField("blinded")
            field.isAccessible = true
            field.set(blindedPost, true)

            every { postRepository.findById(300L) } returns blindedPost

            Then("404 에러가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.getComments(300L, null, 20)
                }
                ex.status shouldBe 404
                ex.message shouldBe "echo.post.not-found"
            }
        }
    }
})

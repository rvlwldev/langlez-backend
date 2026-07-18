package com.langlez.echo.application

import com.langlez.core.LanglezException
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

class EchoServiceTest : BehaviorSpec({

    val postRepository = mockk<PostRepository>()
    val memberRepo = mockk<MemberRepository>()
    val relationshipRepository = mockk<RelationshipRepository>()
    val hashtagTrendRepository = mockk<HashtagTrendRepository>(relaxed = true)
    val dailyRateLimiter = mockk<DailyRateLimiter>()
    val commentRepository = mockk<CommentRepository>()

    val service = EchoService(
        postRepository,
        memberRepo,
        relationshipRepository,
        hashtagTrendRepository,
        dailyRateLimiter,
        commentRepository,
    )

    Given("게시물 작성 시") {
        val authorId = 1L
        val content = "오늘 날씨 정말 좋네요! #날씨 #주말"
        val media = listOf("http://s3.com/image1.png" to PostMedia.Type.IMAGE)

        val authorMember = mockk<Member>()
        every { authorMember.id } returns authorId
        every { authorMember.role } returns Member.Role.MEMBER
        every { memberRepo.findById(authorId) } returns authorMember
        every { dailyRateLimiter.tryConsume(any(), any()) } returns true

        every { postRepository.save(any()) } answers {
            val post = firstArg<Post>()
            Post(id = 100L, authorId = post.authorId, content = post.content, createdAt = post.createdAt)
        }
        every { postRepository.saveMediaAll(any()) } answers { firstArg() }
        every { postRepository.findHashtagByName("날씨") } returns null
        every { postRepository.findHashtagByName("주말") } returns null
        every { postRepository.saveHashtag(any()) } answers {
            val hashtag = firstArg<Hashtag>()
            Hashtag(id = 50L, name = hashtag.name, createdAt = hashtag.createdAt)
        }
        every { postRepository.savePostHashtag(any()) } answers { firstArg() }

        When("정상적인 본문과 미디어를 전달하면") {
            val result = service.createPost(authorId, content, media)

            Then("게시물이 정상적으로 저장되어야 한다") {
                result shouldNotBe null
                result.id shouldBe 100L
                result.content shouldBe content
                verify(exactly = 1) { postRepository.save(any()) }
                verify(exactly = 1) { postRepository.saveMediaAll(any()) }
            }

            Then("해쉬태그가 파싱되어 저장되고 사용이 기록되어야 한다") {
                verify(exactly = 2) { postRepository.saveHashtag(any()) }
                verify(exactly = 2) { postRepository.savePostHashtag(any()) }
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
        every { postRepository.save(any()) } answers { firstArg() }

        When("처음 좋아요를 누르면") {
            every { postRepository.findLike(memberId, postId) } returns null
            every { postRepository.saveLike(any()) } answers { firstArg() }

            service.likePost(memberId, postId)

            Then("좋아요 정보가 저장되고 카운트가 증가해야 한다") {
                post.likeCount shouldBe 1L
                verify(exactly = 1) { postRepository.saveLike(any()) }
            }
        }

        When("이미 누른 상태에서 다시 좋아요를 누르면") {
            every { postRepository.findLike(memberId, postId) } returns PostLike(postId = postId, memberId = memberId)

            service.likePost(memberId, postId)

            Then("추가 저장 없이 무시된다") {
                post.likeCount shouldBe 1L
                verify(exactly = 1) { postRepository.saveLike(any()) } // 누적 호출 횟수 유지
            }
        }

        When("좋아요를 취소하면") {
            every { postRepository.findLike(memberId, postId) } returns PostLike(postId = postId, memberId = memberId)
            every { postRepository.deleteLike(memberId, postId) } returns Unit

            service.unlikePost(memberId, postId)

            Then("좋아요 정보가 제거되고 카운트가 감소해야 한다") {
                post.likeCount shouldBe 0L
                verify(exactly = 1) { postRepository.deleteLike(memberId, postId) }
            }
        }
    }

    Given("게시물 신고 시") {
        val postId = 100L
        val post = Post(id = postId, authorId = 10L, content = "신고 대상 글")

        every { postRepository.findById(postId) } returns post
        every { postRepository.saveReport(any()) } answers { firstArg() }
        every { postRepository.save(any()) } answers { firstArg() }

        When("동일 유저가 이미 신고한 게시물을 다시 신고하면") {
            every { postRepository.findReport(1L, postId) } returns PostReport(postId = postId, reporterId = 1L, reason = "spam")

            Then("예외가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.reportPost(1L, postId, "spam")
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.report.already-reported"
            }
        }

        When("서로 다른 유저가 5회 이상 신고하면") {
            every { postRepository.findReport(any(), postId) } returns null

            (1..4).forEach { reporterId ->
                service.reportPost(reporterId.toLong(), postId, "reason")
            }
            post.blinded shouldBe false

            service.reportPost(5L, postId, "reason")

            Then("게시물이 블라인드 처리되어야 한다") {
                post.blinded shouldBe true
                post.blindedAt shouldNotBe null
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

        When("days가 1, 7, 30이 아닌 값(예: 5)이면") {
            Then("400 예외(echo.trending.invalid-days)가 발생해야 한다") {
                val ex = shouldThrow<LanglezException> {
                    service.getTrendingHashtags(5, 10)
                }
                ex.status shouldBe 400
                ex.message shouldBe "echo.trending.invalid-days"
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
        val authorId = 1L
        val postId = 100L
        val post = Post(id = postId, authorId = 2L, content = "게시글")

        every { postRepository.findById(postId) } returns post
        every { postRepository.findById(999L) } returns null

        every { commentRepository.save(any()) } answers {
            val comment = firstArg<Comment>()
            Comment(id = 200L, postId = comment.postId, authorId = comment.authorId, content = comment.content, createdAt = comment.createdAt)
        }

        When("정상적으로 댓글을 작성하면") {
            val result = service.addComment(authorId, postId, "댓글 내용")

            Then("댓글이 저장되고 저장된 댓글이 반환된다") {
                result shouldNotBe null
                result.id shouldBe 200L
                result.content shouldBe "댓글 내용"
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

            val authorMember = mockk<Member>()
            every { authorMember.id } returns authorId
            every { authorMember.username } returns "commenter"
            every { authorMember.nickname } returns "CommenterNick"
            every { memberRepo.findByIds(listOf(authorId)) } returns listOf(authorMember)

            val result = service.getComments(postId, null, 20)

            Then("작성자의 username/nickname 매핑을 확인한다") {
                result.comments.size shouldBe 1
                result.comments[0].commentId shouldBe 200L
                result.comments[0].username shouldBe "commenter"
                result.comments[0].nickname shouldBe "CommenterNick"
                result.comments[0].content shouldBe "댓글 내용"
            }
        }
    }
})

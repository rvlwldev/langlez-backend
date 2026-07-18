package com.langlez.echo.application

import com.langlez.core.LanglezException
import com.langlez.echo.domain.*
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
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

    val service = EchoService(
        postRepository,
        memberRepo,
        relationshipRepository,
        hashtagTrendRepository
    )

    Given("게시물 작성 시") {
        val authorId = 1L
        val content = "오늘 날씨 정말 좋네요! #날씨 #주말"
        val media = listOf("http://s3.com/image1.png" to PostMedia.Type.IMAGE)

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
})

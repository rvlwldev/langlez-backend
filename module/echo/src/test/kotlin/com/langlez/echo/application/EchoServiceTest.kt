package com.langlez.echo.application

import com.langlez.relationship.contract.BlockReader
import com.langlez.relationship.contract.FollowReader
import com.langlez.attachment.contract.Storage
import com.langlez.echo.contract.EchoCommentCreatedEvent
import com.langlez.echo.contract.EchoPostLikedEvent
import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.EchoRepository
import com.langlez.echo.domain.Hashtag
import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostMedia
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class EchoServiceTest : BehaviorSpec({

    val repo = mockk<EchoRepository>()
    val follows = mockk<FollowReader>()
    val blocks = mockk<BlockReader>()
    val storage = mockk<Storage>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // 글 작성은 첨부 확정(블로킹 I/O)을 트랜잭션 밖에서 끝내고 저장만 묶는다. 테스트에선 콜백을 그대로 실행한다.
    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers {
        firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true))
    }

    val service = EchoService(repo, follows, blocks, storage, publisher, tx)

    afterEach { clearMocks(repo, follows, blocks, storage, publisher, answers = false) }

    val me = 1L
    val other = 2L

    // 목록은 라운드마다 blockedAmong 으로 한 번에 묻는다. 각 테스트는 단건 스텁만 두고,
    // 배치 판정은 그 단건 스텁에 그대로 위임한다 — 두 판정이 갈리면 그게 곧 차단 구멍이다.
    every { blocks.blockedAmong(me, any()) } answers {
        secondArg<Collection<Long>>().filterTo(mutableSetOf()) { blocks.isBlockedBetween(me, it) }
    }

    fun post(id: Long = 10L, authorId: Long = me, content: String = "hello") =
        Post(id = id, authorId = authorId, content = content)

    Given("글을 쓸 때") {

        When("첨부 key 를 함께 보내면") {
            Then("key 로 확정한 URL 만 저장한다 (클라이언트가 준 문자열을 그대로 쓰지 않는다)") {
                val media = slot<Collection<PostMedia>>()

                every { storage.attach("echo/a.jpg", null) } returns "https://cdn.test/echo/a.jpg"
                every { repo.save(any<Post>()) } returns post()
                every { repo.saveMedia(capture(media)) } returns Unit
                every { repo.findOrCreateHashtags(any()) } returns emptyList()

                service.createPost(me, "hello", listOf("echo/a.jpg"))

                verify { storage.attach("echo/a.jpg", null) }
                media.captured.map(PostMedia::url) shouldContainExactly listOf("https://cdn.test/echo/a.jpg")
            }
        }

        When("본문에 해시태그가 있으면") {
            Then("추출해서 글에 연결한다") {
                every { repo.save(any<Post>()) } returns post(content = "#Seoul 에서 #커피 한잔 #seoul")
                every { repo.findOrCreateHashtags(setOf("seoul", "커피")) } returns
                    listOf(Hashtag(id = 7, name = "seoul"), Hashtag(id = 8, name = "커피"))
                every { repo.linkHashtags(10L, listOf(7L, 8L)) } returns Unit

                service.createPost(me, "#Seoul 에서 #커피 한잔 #seoul", emptyList())

                verify { repo.linkHashtags(10L, listOf(7L, 8L)) }
            }
        }

        When("본문도 첨부도 비어 있으면") {
            Then("400 으로 거절한다") {
                val ex = shouldThrow<LanglezException> { service.createPost(me, "   ", emptyList()) }
                ex.status.value() shouldBe 400
                verify(exactly = 0) { repo.save(any<Post>()) }
            }
        }
    }

    Given("글을 지울 때") {

        When("남의 글이면") {
            Then("403 이고 저장소를 건드리지 않는다") {
                every { repo.findPost(10L) } returns post(authorId = other)

                val ex = shouldThrow<LanglezException> { service.deletePost(me, 10L) }
                ex.status.value() shouldBe 403
                verify(exactly = 0) { repo.save(any<Post>()) }
            }
        }

        When("내 글이면") {
            Then("삭제 표시하고 저장한다") {
                val mine = post()
                every { repo.findPost(10L) } returns mine
                every { repo.save(mine) } returns mine

                service.deletePost(me, 10L)

                mine.deletedAt shouldNotBe null
                verify { repo.save(mine) }
            }
        }
    }

    Given("홈 타임라인을 볼 때") {

        When("차단 관계인 사람의 글이 섞여 있으면") {
            Then("그 글만 빠진다") {
                every { follows.followingIds(me) } returns listOf(other, 3L)
                every { repo.findPosts(listOf(other, 3L), 20, null) } returns
                    listOf(post(id = 12, authorId = 3L), post(id = 11, authorId = other))
                every { blocks.isBlockedBetween(me, other) } returns true
                every { blocks.isBlockedBetween(me, 3L) } returns false
                every { repo.findMedia(listOf(12L)) } returns emptyList()
                every { repo.findLikedPostIds(me, listOf(12L)) } returns emptySet()

                val timeline = service.homeTimeline(me, 20, null)

                timeline shouldHaveSize 1
                timeline.first().id shouldBe 12L
            }
        }

        When("커서를 넘기면") {
            Then("post id 커서 그대로 저장소에 전달된다 (created_at 이 아니다)") {
                every { follows.followingIds(me) } returns listOf(other)
                every { repo.findPosts(listOf(other), 20, 11L) } returns emptyList()

                service.homeTimeline(me, 20, 11L).shouldBeEmpty()

                verify { repo.findPosts(listOf(other), 20, 11L) }
            }
        }

        When("팔로우하는 사람이 없으면") {
            Then("저장소를 조회하지 않고 빈 목록을 준다") {
                every { follows.followingIds(me) } returns emptyList()

                service.homeTimeline(me, 20, null).shouldBeEmpty()

                verify(exactly = 0) { repo.findPosts(any(), any(), any()) }
            }
        }

        When("차단된 작성자의 글 때문에 첫 페이지가 부족하면") {
            Then("다음 페이지를 더 가져와 요청한 size 를 채운다") {
                every { follows.followingIds(me) } returns listOf(other, 3L)
                every { repo.findPosts(listOf(other, 3L), 2, null) } returns
                    listOf(post(id = 22, authorId = other), post(id = 21, authorId = 3L))
                every { repo.findPosts(listOf(other, 3L), 1, 21L) } returns
                    listOf(post(id = 20, authorId = 3L))
                every { blocks.isBlockedBetween(me, other) } returns true
                every { blocks.isBlockedBetween(me, 3L) } returns false
                every { repo.findMedia(listOf(21L, 20L)) } returns emptyList()
                every { repo.findLikedPostIds(me, listOf(21L, 20L)) } returns emptySet()

                val timeline = service.homeTimeline(me, 2, null)

                timeline shouldHaveSize 2
                timeline.map { it.id } shouldContainExactly listOf(21L, 20L)
            }
        }
    }

    Given("해시태그 타임라인을 볼 때") {

        When("차단된 작성자의 글 때문에 첫 페이지가 부족하면") {
            Then("다음 페이지를 더 가져와 요청한 size 를 채운다") {
                every { repo.findPostsByHashtag("seoul", 2, null) } returns
                    listOf(post(id = 32, authorId = other), post(id = 31, authorId = 3L))
                every { repo.findPostsByHashtag("seoul", 1, 31L) } returns
                    listOf(post(id = 30, authorId = 3L))
                every { blocks.isBlockedBetween(me, other) } returns true
                every { blocks.isBlockedBetween(me, 3L) } returns false
                every { repo.findMedia(listOf(31L, 30L)) } returns emptyList()
                every { repo.findLikedPostIds(me, listOf(31L, 30L)) } returns emptySet()

                val timeline = service.hashtagTimeline(me, "#Seoul", 2, null)

                timeline shouldHaveSize 2
                timeline.map { it.id } shouldContainExactly listOf(31L, 30L)
            }
        }
    }

    Given("좋아요를 누를 때") {

        When("이미 누른 글이면") {
            Then("409 이고 좋아요를 다시 넣지 않는다") {
                every { repo.findPost(10L) } returns post(authorId = other)
                every { blocks.isBlockedBetween(me, other) } returns false
                every { repo.isLiked(10L, me) } returns true

                val ex = shouldThrow<LanglezException> { service.like(me, 10L) }
                ex.status.value() shouldBe 409
                verify(exactly = 0) { repo.addLike(any(), any()) }
            }
        }

        When("처음 누르는 글이면") {
            Then("좋아요를 넣고 알림 이벤트를 발행한다") {
                every { repo.findPost(10L) } returns post(authorId = other)
                every { blocks.isBlockedBetween(me, other) } returns false
                every { repo.isLiked(10L, me) } returns false
                every { repo.addLike(10L, me) } returns Unit

                service.like(me, 10L)

                verify { repo.addLike(10L, me) }
                verify { publisher.publishEvent(EchoPostLikedEvent(10L, other, me)) }
            }
        }
    }

    Given("댓글을 다룰 때") {

        When("남의 댓글을 지우려 하면") {
            Then("403 이다") {
                every { repo.findComment(5L) } returns Comment(id = 5, postId = 10, authorId = other, content = "hi")

                val ex = shouldThrow<LanglezException> { service.deleteComment(me, 5L) }
                ex.status.value() shouldBe 403
                verify(exactly = 0) { repo.save(any<Comment>()) }
            }
        }

        When("댓글을 달면") {
            Then("글쓴이에게 갈 알림 이벤트를 발행한다") {
                val saved = Comment(id = 5, postId = 10, authorId = me, content = "hi")
                every { repo.findPost(10L) } returns post(authorId = other)
                every { blocks.isBlockedBetween(me, other) } returns false
                every { repo.save(any<Comment>()) } returns saved

                service.comment(me, 10L, "hi")

                verify { publisher.publishEvent(EchoCommentCreatedEvent(10L, other, 5L, me, "hi")) }
            }
        }

        When("차단된 작성자의 댓글 때문에 첫 페이지가 부족하면") {
            Then("다음 페이지를 더 가져와 요청한 size 를 채운다") {
                every { repo.findPost(10L) } returns post(id = 10L, authorId = 99L)
                every { blocks.isBlockedBetween(me, 99L) } returns false
                every { repo.findComments(10L, 2, null) } returns listOf(
                    Comment(id = 1, postId = 10, authorId = other, content = "a"),
                    Comment(id = 2, postId = 10, authorId = 3L, content = "b"),
                )
                every { repo.findComments(10L, 1, 2L) } returns
                    listOf(Comment(id = 3, postId = 10, authorId = 3L, content = "c"))
                every { blocks.isBlockedBetween(me, other) } returns true
                every { blocks.isBlockedBetween(me, 3L) } returns false

                val comments = service.listComments(me, 10L, 2, null)

                comments shouldHaveSize 2
                comments.map { it.id } shouldContainExactly listOf(2L, 3L)
            }
        }
    }

    Given("첨부 업로드 URL 을 받을 때") {

        When("사진도 영상도 아니면") {
            Then("400 으로 거절한다") {
                val ex = shouldThrow<LanglezException> { service.presignUpload(me, "a.pdf", "application/pdf") }
                ex.status.value() shouldBe 400
                verify(exactly = 0) { storage.presign(any(), any(), any(), any()) }
            }
        }

        When("이미지면") {
            Then("key 와 업로드 URL 을 함께 돌려준다") {
                every { storage.presign(me, "echo", Storage.Type.IMAGE, "a.jpg") } returns
                    Storage.PresignedResult("echo/a.jpg", "https://presigned.test/a.jpg")

                service.presignUpload(me, "a.jpg", "image/jpeg").key shouldBe "echo/a.jpg"
                verify { storage.presign(me, "echo", Storage.Type.IMAGE, "a.jpg") }
            }
        }
    }
})

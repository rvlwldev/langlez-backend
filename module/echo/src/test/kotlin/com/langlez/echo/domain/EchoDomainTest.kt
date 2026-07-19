package com.langlez.echo.domain

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.time.Instant

class EchoDomainTest : BehaviorSpec({

    Given("Post 엔티티가 주어졌을 때") {
        val post = Post(id = 1L, authorId = 100L, content = "테스트 게시글")

        When("delete()를 최초로 호출하면") {
            val now = Instant.now()
            post.delete(now)

            Then("deletedAt 필드가 해당 시각으로 업데이트된다") {
                post.deletedAt shouldBe now
            }
        }

        When("이미 삭제된 Post에 delete()를 다시 호출하면") {
            val originalDeletedAt = post.deletedAt
            val later = Instant.now().plusSeconds(10)
            post.delete(later)

            Then("deletedAt 필드가 변경되지 않고 보존된다 (Idempotent)") {
                post.deletedAt shouldBe originalDeletedAt
            }
        }
    }

    Given("Comment 엔티티가 주어졌을 때") {
        val comment = Comment(id = 1L, postId = 10L, authorId = 100L, content = "테스트 댓글")

        When("delete()를 최초로 호출하면") {
            val now = Instant.now()
            comment.delete(now)

            Then("deletedAt 필드가 해당 시각으로 업데이트된다") {
                comment.deletedAt shouldBe now
            }
        }

        When("이미 삭제된 Comment에 delete()를 다시 호출하면") {
            val originalDeletedAt = comment.deletedAt
            val later = Instant.now().plusSeconds(10)
            comment.delete(later)

            Then("deletedAt 필드가 변경되지 않고 보존된다 (Idempotent)") {
                comment.deletedAt shouldBe originalDeletedAt
            }
        }
    }
})

package com.langlez.chat.domain

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

class ChatDomainTest : BehaviorSpec({
    Given("메시지를 보낸 사람이 삭제하면") {
        val m = ChatMessage(roomId = 1L, senderId = 10L, seq = 1L, type = ChatMessage.Type.TEXT, content = "oops")
        m.delete(requesterId = 10L)
        Then("삭제 시각이 남고 내용이 가려진다") {
            m.deletedAt.shouldNotBeNull()
            m.isDeleted() shouldBe true
        }
    }
    Given("남이 삭제하려 하면") {
        val m = ChatMessage(roomId = 1L, senderId = 10L, seq = 1L, type = ChatMessage.Type.TEXT, content = "hi")
        Then("거부된다") { shouldThrow<IllegalArgumentException> { m.delete(requesterId = 99L) } }
    }
    Given("메시지를 발행하면") {
        val m = ChatMessage(roomId = 1L, senderId = 10L, seq = 1L, type = ChatMessage.Type.TEXT, content = "hi")
        m.markPublished()
        // 별도 아웃박스 행 없이 이 플래그가 발행 여부를 기억한다. 단일 문서 쓰기라 원자적이다.
        Then("발행 표시가 남는다") { m.published shouldBe true }
    }
    Given("참여자가 나갔다가 상대가 메시지를 보내면") {
        val p = ChatRoomMember(roomId = 1L, memberId = 10L).apply { leave(Instant.now()) }
        p.rejoin()
        Then("재입장하며 이전 대화가 그대로 보인다(leftAt 해제)") { p.leftAt shouldBe null }
    }
    Given("메시지를 받고 나중에 읽으면") {
        val p = ChatRoomMember(roomId = 1L, memberId = 10L, unreadCount = 2)
        Then("안 읽은 수가 쌓였다가 읽음 처리에서 0 이 된다") {
            p.unreadCount shouldBe 2
            p.markRead(Instant.now())
            p.unreadCount shouldBe 0
        }
    }
})

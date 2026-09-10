package com.langlez.chat.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoomMember
import com.langlez.member.contract.OnlineTracker
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

class ChatMessagePublisherTest : BehaviorSpec({

    val messages = mockk<ChatMessageRepository>()
    val repo = mockk<ChatRepository>()
    val tracker = mockk<OnlineTracker>()
    val kafka = mockk<KafkaTemplate<String, String>>()

    val publisher = ChatMessagePublisher(messages, repo, tracker, kafka, ObjectMapper())

    afterEach { clearMocks(messages, repo, tracker, kafka, answers = false) }

    fun message(roomId: Long = 100L, senderId: Long = 1L) =
        ChatMessage(roomId, senderId, 7L, ChatMessage.Type.TEXT, "안녕").apply { id = "m7" }

    fun participants(roomId: Long = 100L) = listOf(ChatRoomMember(roomId, 1L), ChatRoomMember(roomId, 2L))

    fun sent() = CompletableFuture.completedFuture(mockk<SendResult<String, String>>())

    Given("수신자가 그 방을 보고 있으면") {

        When("발행기가 돌면") {
            Then("카프카로 아무것도 보내지 않고 발행 표시만 한다 (메시지는 이미 화면에 떴다)") {
                val message = message()
                every { messages.findUnpublished(any()) } returns listOf(message)
                every { repo.findParticipants(100L) } returns participants()
                every { tracker.viewers("/topic/chat/room/100") } returns setOf(2L)
                every { messages.save(any()) } answers { firstArg() }

                publisher.publish()

                verify(exactly = 0) { kafka.send(any(), any<String>(), any()) }
                verify { messages.save(message) }
                message.published shouldBe true
            }
        }
    }

    Given("수신자가 그 방을 보고 있지 않으면") {

        When("발행기가 돌면") {
            Then("메시지 전송 이벤트를 방 id 키로 발행하고 발행 표시한다") {
                val message = message()
                val payload = slot<String>()
                every { messages.findUnpublished(any()) } returns listOf(message)
                every { repo.findParticipants(100L) } returns participants()
                every { tracker.viewers("/topic/chat/room/100") } returns emptySet()
                every { kafka.send("chat-message-sent", "100", capture(payload)) } returns sent()
                every { messages.save(any()) } answers { firstArg() }

                publisher.publish()

                verify { kafka.send("chat-message-sent", "100", any()) }
                verify { messages.save(message) }
                payload.captured shouldContain "\"messageId\":\"m7\""
                payload.captured shouldContain "\"recipientId\":2"
                message.published shouldBe true
            }
        }
    }

    Given("카프카 발행이 실패하면") {

        When("발행기가 돌면") {
            Then("published 가 false 로 남아 다음 주기에 다시 잡힌다") {
                val message = message()
                every { messages.findUnpublished(any()) } returns listOf(message)
                every { repo.findParticipants(100L) } returns participants()
                every { tracker.viewers(any()) } returns emptySet()
                every { kafka.send(any(), any<String>(), any()) } returns
                    CompletableFuture.failedFuture(RuntimeException("broker down"))

                publisher.publish()

                verify(exactly = 0) { messages.save(any()) }
                message.published shouldBe false
            }
        }
    }

    Given("한 메시지의 저장(Mongo)이 실패해도 (B-17)") {

        When("발행기가 돌면") {
            Then("예외가 밖으로 새어 나가지 않고 다음 메시지 처리를 계속 진행한다") {
                val m1 = message().apply { id = "m1" }
                val m2 = message().apply { id = "m2" }
                every { messages.findUnpublished(any()) } returns listOf(m1, m2)
                every { repo.findParticipants(100L) } returns participants()
                every { tracker.viewers(any()) } returns emptySet()
                every { kafka.send(any(), any<String>(), any()) } returns sent()
                every { messages.save(m1) } throws RuntimeException("Mongo write error")
                every { messages.save(m2) } answers { firstArg() }

                publisher.publish()

                verify { messages.save(m1) }
                verify { messages.save(m2) }
                m2.published shouldBe true
            }
        }
    }
})

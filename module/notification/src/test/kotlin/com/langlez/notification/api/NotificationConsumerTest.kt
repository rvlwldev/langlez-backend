package com.langlez.notification.api

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.langlez.core.event.chat.ChatMessageSentEvent
import com.langlez.notification.application.NotificationService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify

class NotificationConsumerTest : BehaviorSpec({

    val service = mockk<NotificationService>(relaxed = true)
    val consumer = NotificationConsumer(service, jacksonObjectMapper())

    afterEach { clearMocks(service, answers = false) }

    Given("chat-message-sent 토픽 메시지가 들어오면") {

        When("페이로드가 ChatMessageSentEvent JSON 이면") {
            Then("이벤트로 바꿔 서비스에 넘긴다") {
                val payload =
                    """{"roomId":7,"messageId":"m1","senderId":1,"recipientId":2,"preview":"안녕"}"""

                consumer.onChatMessageSent(payload)

                verify { service.onChatMessage(ChatMessageSentEvent(7L, "m1", 1L, 2L, "안녕")) }
            }
        }
    }
})

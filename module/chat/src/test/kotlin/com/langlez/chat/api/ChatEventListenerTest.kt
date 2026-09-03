package com.langlez.chat.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.infrastructure.jpa.ChatOutBoxRepository
import com.langlez.chat.infrastructure.outbox.ChatOutBox
import com.langlez.chat.contract.ChatUserReportedEvent
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

class ChatEventListenerTest : BehaviorSpec({

    val repo = mockk<ChatOutBoxRepository>()
    val listener = ChatEventListener(repo, ObjectMapper())

    afterEach { clearMocks(repo, answers = false) }

    Given("신고 이벤트가 발행되면") {

        When("리스너가 받으면") {
            Then("chat-user-reported 아웃박스 행이 남는다 (report 가 카프카로 받아 저장한다)") {
                val saved = slot<ChatOutBox>()
                every { repo.save(capture(saved)) } answers { firstArg() }

                listener.onUserReported(ChatUserReportedEvent(100L, 1L, 2L, "욕설", "m7"))

                saved.captured.topic shouldBe "chat-user-reported"
                saved.captured.key shouldBe "100"
                saved.captured.payload!! shouldContain "\"reportedUserId\":2"
            }
        }
    }
})

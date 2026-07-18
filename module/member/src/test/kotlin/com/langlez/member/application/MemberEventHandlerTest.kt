package com.langlez.member.application

import com.langlez.member.outbox.MemberOutBoxRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify

class MemberEventHandlerTest : BehaviorSpec({

    val outboxRepo = mockk<MemberOutBoxRepository>(relaxed = true)
    val handler = MemberEventHandler(outboxRepo)

    afterEach { clearMocks(outboxRepo, answers = false) }

    Given("MemberEventHandler가 이벤트를 처리할 때") {

        When("Created 이벤트가 발행되면") {
            val event = MemberEvent.Created(1L, "test@example.com", "username", "nickname")
            handler.handle(event)

            Then("MEMBER 타입의 member-created 이벤트로 outbox에 저장한다") {
                verify {
                    outboxRepo.save("MEMBER", "1", "member-created", event)
                }
            }
        }

        When("UsernameChanged 이벤트가 발행되면") {
            val event = MemberEvent.UsernameChanged(1L, "newusername")
            handler.handle(event)

            Then("MEMBER 타입의 member-username-changed 이벤트로 outbox에 저장한다") {
                verify {
                    outboxRepo.save("MEMBER", "1", "member-username-changed", event)
                }
            }
        }

        When("NicknameChanged 이벤트가 발행되면") {
            val event = MemberEvent.NicknameChanged(1L, "newnickname")
            handler.handle(event)

            Then("MEMBER 타입의 member-nickname-changed 이벤트로 outbox에 저장한다") {
                verify {
                    outboxRepo.save("MEMBER", "1", "member-nickname-changed", event)
                }
            }
        }
    }
})

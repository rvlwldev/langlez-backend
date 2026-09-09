package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoomMember
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Instant

class ChatSubscriptionAuthorizerTest : BehaviorSpec({

    val repo = mockk<ChatRepository>()
    val authorizer = ChatSubscriptionAuthorizer(repo)

    Given("채팅방 토픽이면") {

        Then("자기 것이라 판정한다") {
            authorizer.supports("/topic/chat/room/1") shouldBe true
        }

        When("그 방의 활성 참여자면") {
            Then("구독을 허용한다") {
                every { repo.findParticipant(1L, 10L) } returns ChatRoomMember(1L, 10L)

                authorizer.authorize("/topic/chat/room/1", 10L) shouldBe true
            }
        }

        When("방을 나간 참여자면") {
            Then("구독을 거부한다") {
                val leftMember = ChatRoomMember(1L, 20L).apply { leave(Instant.now()) }
                every { repo.findParticipant(1L, 20L) } returns leftMember

                authorizer.authorize("/topic/chat/room/1", 20L) shouldBe false
            }
        }

        When("참여자가 아니면") {
            Then("구독을 거부한다") {
                every { repo.findParticipant(1L, 30L) } returns null

                authorizer.authorize("/topic/chat/room/1", 30L) shouldBe false
            }
        }
    }

    Given("채팅방 토픽이 아니면") {

        When("와일드카드로 전체 방을 구독하려 하면") {
            Then("자기 것이 아니라고 판정한다") {
                authorizer.supports("/topic/chat/room/*") shouldBe false
                authorizer.supports("/topic/chat/room/1/extra") shouldBe false
            }
        }

        When("다른 모듈의 토픽이면") {
            Then("자기 것이 아니라고 판정한다") {
                authorizer.supports("/topic/notification/1") shouldBe false
                authorizer.supports("/topic/wave/1/chat") shouldBe false
            }
        }

        When("목적지에서 방 번호를 추출할 수 없으면") {
            Then("인가를 거부한다") {
                authorizer.authorize("/topic/chat/room/invalid", 10L) shouldBe false
            }
        }
    }
})

package com.langlez.chat.application

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoomMember
import com.langlez.relationship.contract.BlockQuery
import com.langlez.core.MessageBroadcaster
import com.langlez.attachment.contract.Storage
import com.langlez.chat.contract.ChatUserReportedEvent
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

class ChatServiceActionsTest : BehaviorSpec({

    val repo = mockk<ChatRepository>()
    val messages = mockk<ChatMessageRepository>()
    val blocks = mockk<BlockQuery>()
    val storage = mockk<Storage>()
    val broadcaster = mockk<MessageBroadcaster>(relaxed = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = ChatService(repo, messages, blocks, storage, broadcaster, publisher, tx)

    afterEach { clearMocks(repo, messages, blocks, storage, broadcaster, publisher, answers = false) }

    val me = 1L
    val partner = 2L
    val roomId = 100L

    Given("읽음 처리를 하면") {

        When("참여자가 읽으면") {
            Then("읽은 시각이 저장되고 안 읽은 수 카운터가 0 이 된다") {
                val at = Instant.now()
                every { repo.findParticipant(roomId, me) } returns
                    ChatRoomMember(roomId, me, unreadCount = 2)
                every { repo.saveParticipant(any()) } answers { firstArg() }

                service.markRead(me, roomId, at)

                verify { repo.saveParticipant(match { it.lastReadAt == at && it.unreadCount == 0L }) }
            }

            Then("상대 화면에도 읽음이 즉시 반영되도록 브로드캐스트한다") {
                val at = Instant.now()
                every { repo.findParticipant(roomId, me) } returns ChatRoomMember(roomId, me)
                every { repo.saveParticipant(any()) } answers { firstArg() }

                service.markRead(me, roomId, at)

                // 읽음은 저장만 하면 상대가 새로고침해야 알 수 있다. 실시간으로 밀어준다.
                verify {
                    broadcaster.broadcast(
                        "/topic/chat/room/$roomId",
                        match<ChatReadEvent> { it.roomId == roomId && it.memberId == me && it.readAt == at },
                    )
                }
            }
        }

        When("참여자가 아니면") {
            Then("403 으로 거부한다") {
                every { repo.findParticipant(roomId, 999L) } returns null

                shouldThrow<LanglezException> { service.markRead(999L, roomId) }.status.value() shouldBe 403
            }
        }
    }

    Given("방을 나가면") {

        When("참여자가 나가면") {
            Then("나간 시각이 남는다 (대화는 지우지 않는다 — 재입장 정책)") {
                every { repo.findParticipant(roomId, me) } returns ChatRoomMember(roomId, me)
                every { repo.saveParticipant(any()) } answers { firstArg() }

                service.leaveRoom(me, roomId)

                verify { repo.saveParticipant(match { it.leftAt != null }) }
            }
        }
    }

    Given("메시지를 삭제할 때") {

        When("남의 메시지를 지우려 하면") {
            Then("403 으로 거부한다") {
                every { messages.find("m7") } returns
                    ChatMessage(roomId, partner, 1L, ChatMessage.Type.TEXT, "hi").apply { id = "m7" }

                val ex = shouldThrow<LanglezException> { service.deleteMessage(me, "m7") }
                ex.status.value() shouldBe 403
                ex.message shouldBe "chat.message.not-owner"
            }
        }

        When("없는 메시지를 지우려 하면") {
            Then("404 로 거부한다") {
                every { messages.find("none") } returns null

                shouldThrow<LanglezException> { service.deleteMessage(me, "none") }.status.value() shouldBe 404
            }
        }

        When("보낸 사람이 지우면") {
            Then("모두에게 삭제되고 실시간 통지가 나간다") {
                val message = ChatMessage(roomId, me, 1L, ChatMessage.Type.TEXT, "oops").apply { id = "m7" }
                every { messages.find("m7") } returns message
                every { messages.save(any()) } answers { firstArg() }

                service.deleteMessage(me, "m7")

                message.deletedAt.shouldNotBeNull()
                verify {
                    broadcaster.broadcast(
                        "/topic/chat/room/$roomId",
                        match<ChatMessageView> { it.deleted && it.content == null },
                    )
                }
            }
        }
    }

    Given("상대를 신고하면") {

        When("참여자가 신고하면") {
            Then("relationship 을 직접 부르지 않고 이벤트만 발행한다") {
                every { repo.findParticipants(roomId) } returns
                    listOf(ChatRoomMember(roomId, me), ChatRoomMember(roomId, partner))

                service.report(me, roomId, "욕설", "m7")

                verify {
                    publisher.publishEvent(
                        match<ChatUserReportedEvent> {
                            it.reporterId == me && it.reportedUserId == partner &&
                                it.reason == "욕설" && it.triggerMessageId == "m7"
                        }
                    )
                }
            }
        }

        When("참여자가 아니면") {
            Then("403 으로 거부한다") {
                every { repo.findParticipants(roomId) } returns
                    listOf(ChatRoomMember(roomId, me), ChatRoomMember(roomId, partner))

                shouldThrow<LanglezException> { service.report(999L, roomId, "욕설", null) }
                    .status.value() shouldBe 403
            }
        }
    }
})

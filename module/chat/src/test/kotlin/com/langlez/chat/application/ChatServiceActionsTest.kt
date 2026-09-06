package com.langlez.chat.application

import com.langlez.attachment.contract.Storage
import com.langlez.block.contract.BlockReader
import com.langlez.chat.contract.ChatUserReportedEvent
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomMember
import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class ChatServiceActionsTest : BehaviorSpec({

    val repo = mockk<ChatRepository>()
    val messages = mockk<ChatMessageRepository>()
    val blocks = mockk<BlockReader>()
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
                every { messages.findByRoom(roomId, 1, null) } returns listOf(message)
                every { repo.findRoom(roomId) } returns ChatRoom(id = roomId)

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

        When("보낸 사람이 방의 마지막 메시지를 지우면") {
            Then("방 목록 프리뷰에 원문이 남지 않는다") {
                // 운영과 같은 정밀도로 맞춘다. 삭제 경로의 메시지는 Mongo 를 거쳐 와 밀리초까지만 남고,
                // 방 메타는 전송 때 인메모리 Instant 가 Postgres timestamp(6) 로 들어가 마이크로초를 갖는다.
                // 같은 메시지인데 두 값이 다르므로, 단조성 가드를 밀리초로 비교하지 않으면 갱신이 통째로 스킵된다.
                val sentAt = Instant.now()
                val message = ChatMessage(
                    roomId, me, 3L, ChatMessage.Type.TEXT, "010-1234-5678",
                    createdAt = sentAt.truncatedTo(ChronoUnit.MILLIS),
                ).apply { id = "m7" }
                val room = ChatRoom(id = roomId, lastMessageAt = sentAt, lastMessagePreview = "010-1234-5678")

                every { messages.find("m7") } returns message
                every { messages.save(any()) } answers { firstArg() }
                every { messages.findByRoom(roomId, 1, null) } returns listOf(message)
                every { repo.findRoom(roomId) } returns room

                service.deleteMessage(me, "m7")

                // 대화창은 ChatMessageView 가 가리지만 방 목록은 Postgres 에 박힌 이 값을 그대로 보여준다.
                room.lastMessagePreview shouldBe ChatMessage.DELETED_PREVIEW
            }
        }

        When("마지막 메시지라고 판정한 뒤 상대의 새 메시지가 먼저 커밋되면") {
            Then("프리뷰와 lastMessageAt 이 역행하지 않는다") {
                val message = ChatMessage(roomId, me, 3L, ChatMessage.Type.TEXT, "010-1234-5678").apply { id = "m7" }
                // 판정 시점엔 m7 이 마지막이었지만, 트랜잭션에 들어가기 전 send() 가 먼저 커밋해
                // 방 메타는 이미 새 메시지로 넘어가 있다.
                val newerAt = message.createdAt.plusMillis(50)
                val room = ChatRoom(id = roomId, lastMessageAt = newerAt, lastMessagePreview = "그 뒤에 온 말")

                every { messages.find("m7") } returns message
                every { messages.save(any()) } answers { firstArg() }
                every { messages.findByRoom(roomId, 1, null) } returns listOf(message)
                every { repo.findRoom(roomId) } returns room

                service.deleteMessage(me, "m7")

                // 덮었다면 목록 정렬(last_message_at desc)에서 이 방이 과거로 밀린다.
                room.lastMessagePreview shouldBe "그 뒤에 온 말"
                room.lastMessageAt shouldBe newerAt
            }
        }

        When("보낸 사람이 중간 메시지를 지우면") {
            Then("방 프리뷰는 건드리지 않는다") {
                val message = ChatMessage(roomId, me, 3L, ChatMessage.Type.TEXT, "oops").apply { id = "m7" }
                val latest = ChatMessage(roomId, partner, 4L, ChatMessage.Type.TEXT, "그 뒤에 온 말").apply { id = "m8" }
                every { messages.find("m7") } returns message
                every { messages.save(any()) } answers { firstArg() }
                every { messages.findByRoom(roomId, 1, null) } returns listOf(latest)

                service.deleteMessage(me, "m7")

                verify(exactly = 0) { repo.findRoom(any()) }
            }
        }
    }

    Given("상대를 신고하면") {

        When("참여자가 신고하면") {
            Then("report 를 직접 부르지 않고 이벤트만 발행한다") {
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

package com.langlez.chat.application

import com.langlez.attachment.contract.Storage
import com.langlez.block.contract.BlockReader
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomMember
import com.langlez.chat.domain.ChatRoomSummary
import com.langlez.core.MessageBroadcaster
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import java.time.Instant
import org.springframework.context.ApplicationEventPublisher
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class ChatServiceTest : BehaviorSpec({

    val repo = mockk<ChatRepository>()
    val messages = mockk<ChatMessageRepository>()
    val blocks = mockk<BlockReader>()
    val storage = mockk<Storage>()
    val broadcaster = mockk<MessageBroadcaster>(relaxed = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)

    // send 는 첨부 확정(블로킹 I/O)을 트랜잭션 밖에서 끝내고 저장만 묶는다. 테스트에선 콜백을 그대로 실행한다.
    val tx = mockk<TransactionTemplate>()
    every { tx.execute<Any>(any()) } answers { firstArg<TransactionCallback<Any>>().doInTransaction(mockk(relaxed = true)) }

    val service = ChatService(repo, messages, blocks, storage, broadcaster, publisher, tx)

    afterEach { clearMocks(repo, messages, blocks, storage, broadcaster, publisher, answers = false) }

    val me = 1L
    val partner = 2L
    val roomId = 100L

    fun bothParticipants(left: Boolean = false) = listOf(
        ChatRoomMember(roomId, me),
        ChatRoomMember(roomId, partner).apply { if (left) leave(Instant.now()) },
    )

    Given("방을 열 때") {

        When("아직 방이 없으면") {
            Then("새로 만든다") {
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { repo.findRoomBetween(me, partner) } returns null
                every { repo.createRoom(me, partner) } returns ChatRoom(id = roomId)

                service.getOrCreateRoom(me, partner).id shouldBe roomId
                verify { repo.createRoom(me, partner) }
            }
        }

        When("이미 방이 있으면") {
            Then("기존 방을 그대로 쓴다 (방이 두 개 생기지 않는다)") {
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { repo.findRoomBetween(me, partner) } returns ChatRoom(id = roomId)

                service.getOrCreateRoom(me, partner).id shouldBe roomId
                verify(exactly = 0) { repo.createRoom(any(), any()) }
            }
        }

        When("상대가 나 자신이면") {
            Then("400 으로 거부한다") {
                val ex = shouldThrow<LanglezException> { service.getOrCreateRoom(me, me) }
                ex.status.value() shouldBe 400
                ex.message shouldBe "chat.self-room"
            }
        }

        When("차단된 상대면") {
            Then("403 으로 거부한다") {
                every { blocks.isBlockedBetween(me, partner) } returns true

                val ex = shouldThrow<LanglezException> { service.getOrCreateRoom(me, partner) }
                ex.status.value() shouldBe 403
                ex.message shouldBe "chat.blocked"
                verify(exactly = 0) { repo.createRoom(any(), any()) }
            }
        }
    }

    Given("방 목록을 볼 때") {

        When("내가 나간 방이 섞여 있으면") {
            Then("나간 방은 목록에서 빠진다") {
                val stayed = ChatRoomSummary(ChatRoom(id = roomId), partner, 3)
                val left = ChatRoomSummary(ChatRoom(id = 200L), 3L, 0)

                every { repo.findRoomSummaries(me, 10, null) } returns listOf(stayed, left)
                every { repo.findParticipant(roomId, me) } returns ChatRoomMember(roomId, me)
                every { repo.findParticipant(200L, me) } returns
                    ChatRoomMember(200L, me).apply { leave(Instant.now()) }

                service.listRooms(me, 10, null).map { it.room.id } shouldBe listOf(roomId)
            }
        }
    }

    Given("메시지를 조회할 때") {

        When("방 참여자가 아니면") {
            Then("403 으로 거부한다 (남의 방을 훔쳐보지 못한다)") {
                every { repo.findParticipant(roomId, 999L) } returns null

                val ex = shouldThrow<LanglezException> { service.listMessages(999L, roomId, 10, null) }
                ex.status.value() shouldBe 403
                ex.message shouldBe "chat.room.forbidden"
            }
        }

        When("참여자가 조회하면") {
            Then("첨부가 임베드돼 있어 추가 조회 없이 URL 이 함께 나온다") {
                val message = ChatMessage(
                    roomId, partner, 1L, ChatMessage.Type.IMAGE,
                    files = listOf(ChatMessage.Attachment("https://cdn/1.jpg", 0)),
                ).apply { id = "m1" }

                every { repo.findParticipant(roomId, me) } returns ChatRoomMember(roomId, me)
                every { messages.findByRoom(roomId, 10, null) } returns listOf(message)

                val views = service.listMessages(me, roomId, 10, null)
                views.single().fileUrls shouldBe listOf("https://cdn/1.jpg")
                views.single().deleted shouldBe false
            }
        }

        When("삭제된 메시지가 섞여 있으면") {
            Then("내용도 첨부도 내보내지 않는다") {
                val deleted = ChatMessage(roomId, partner, 1L, ChatMessage.Type.TEXT, "secret")
                    .apply { id = "m1"; delete(partner) }

                every { repo.findParticipant(roomId, me) } returns ChatRoomMember(roomId, me)
                every { messages.findByRoom(roomId, 10, null) } returns listOf(deleted)

                val view = service.listMessages(me, roomId, 10, null).single()
                view.deleted shouldBe true
                view.content shouldBe null
                view.fileUrls.shouldBeEmpty()
            }
        }
    }

    Given("메시지를 보낼 때") {

        When("방 참여자가 아니면") {
            Then("403 으로 거부한다") {
                every { repo.findParticipants(roomId) } returns bothParticipants()

                val ex = shouldThrow<LanglezException> {
                    service.send(999L, roomId, ChatMessage.Type.TEXT, "hi", emptyList())
                }
                ex.status.value() shouldBe 403
                ex.message shouldBe "chat.room.forbidden"
            }
        }

        When("내용도 첨부도 없으면") {
            Then("400 으로 거부한다") {
                val ex = shouldThrow<LanglezException> {
                    service.send(me, roomId, ChatMessage.Type.TEXT, "  ", emptyList())
                }
                ex.status.value() shouldBe 400
                ex.message shouldBe "chat.message.empty"
            }
        }

        When("차단된 상대에게 보내면") {
            Then("403 으로 거부한다") {
                every { repo.findParticipants(roomId) } returns bothParticipants()
                every { blocks.isBlockedBetween(me, partner) } returns true

                val ex = shouldThrow<LanglezException> {
                    service.send(me, roomId, ChatMessage.Type.TEXT, "hi", emptyList())
                }
                ex.status.value() shouldBe 403
                ex.message shouldBe "chat.blocked"
            }
        }

        When("사진을 붙여 보내면") {
            Then("첨부는 트랜잭션 밖에서 확정되고, 저장 뒤 방 미리보기 갱신·브로드캐스트가 일어난다") {
                val room = ChatRoom(id = roomId)

                every { repo.findParticipants(roomId) } returns bothParticipants()
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { storage.attach("k1", any()) } returns "https://cdn/k1.jpg"
                every { repo.findRoom(roomId) } returns room
                every { repo.saveParticipant(any()) } answers { firstArg() }
                every { repo.increaseUnread(any(), any()) } returns Unit
                every { messages.nextSeq(roomId) } returns 7L
                every { messages.save(any()) } answers { firstArg<ChatMessage>().apply { id = "m1" } }

                val view = service.send(me, roomId, ChatMessage.Type.IMAGE, null, listOf("k1"))

                view.fileUrls shouldBe listOf("https://cdn/k1.jpg")
                view.seq shouldBe 7L
                room.lastMessagePreview shouldBe "[IMAGE]"
                verify {
                    messages.save(
                        match {
                            it.senderId == me && it.roomId == roomId && it.seq == 7L &&
                                it.files.map(ChatMessage.Attachment::url) == listOf("https://cdn/k1.jpg")
                        }
                    )
                }
                verify { broadcaster.broadcast("/topic/chat/room/$roomId", view) }
            }

            // 발행 판정은 Task 6 의 폴러가 한다. 전송 시점에 정하면 그 사이 상대의 화면 상태 변화를 못 잡는다.
            Then("알림 이벤트를 여기서 발행하지 않고 미발행 상태로 남긴다") {
                every { repo.findParticipants(roomId) } returns bothParticipants()
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { repo.findRoom(roomId) } returns ChatRoom(id = roomId)
                every { repo.saveParticipant(any()) } answers { firstArg() }
                every { repo.increaseUnread(any(), any()) } returns Unit
                every { messages.nextSeq(roomId) } returns 1L
                every { messages.save(any()) } answers { firstArg<ChatMessage>().apply { id = "m1" } }

                service.send(me, roomId, ChatMessage.Type.TEXT, "hi", emptyList())

                verify { messages.save(match { !it.published }) }
                verify(exactly = 0) { publisher.publishEvent(any()) }
            }
        }

        When("전송에 성공하면") {
            Then("Mongo 저장이 Postgres 갱신보다 먼저 일어난다") {
                every { repo.findParticipants(roomId) } returns bothParticipants()
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { repo.findRoom(roomId) } returns ChatRoom(id = roomId)
                every { repo.saveParticipant(any()) } answers { firstArg() }
                every { repo.increaseUnread(any(), any()) } returns Unit
                every { messages.nextSeq(roomId) } returns 1L
                every { messages.save(any()) } answers { firstArg<ChatMessage>().apply { id = "m1" } }

                service.send(me, roomId, ChatMessage.Type.TEXT, "hi", emptyList())

                // Postgres 가 먼저면 실패했을 때 "목록엔 보이는데 열면 없는 메시지"가 된다.
                verifyOrder {
                    messages.save(any())
                    repo.findRoom(roomId)
                    repo.increaseUnread(any(), any())
                }
            }

            Then("상대의 안 읽은 수가 1 늘어난다") {
                val participants = bothParticipants()

                every { repo.findParticipants(roomId) } returns participants
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { repo.findRoom(roomId) } returns ChatRoom(id = roomId)
                every { repo.saveParticipant(any()) } answers { firstArg() }
                every { repo.increaseUnread(any(), any()) } returns Unit
                every { messages.nextSeq(roomId) } returns 1L
                every { messages.save(any()) } answers { firstArg<ChatMessage>().apply { id = "m1" } }

                service.send(me, roomId, ChatMessage.Type.TEXT, "hi", emptyList())

                // 카운터는 DB 에서 더한다(동시 전송 시 유실 방지)
                verify { repo.increaseUnread(roomId, partner) }
            }
        }

        When("Postgres 갱신이 실패하면") {
            Then("메시지는 Mongo 에 남는다 (대사 스케줄러가 복구한다)") {
                every { repo.findParticipants(roomId) } returns bothParticipants()
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { messages.nextSeq(roomId) } returns 1L
                every { messages.save(any()) } answers { firstArg<ChatMessage>().apply { id = "m1" } }
                every { repo.findRoom(roomId) } throws IllegalStateException("db down")

                shouldThrow<IllegalStateException> {
                    service.send(me, roomId, ChatMessage.Type.TEXT, "hi", emptyList())
                }

                verify { messages.save(any()) }
            }
        }

        When("상대가 나간 방에 보내면") {
            Then("상대가 재입장되어 방이 되살아난다") {
                val participants = bothParticipants(left = true)

                every { repo.findParticipants(roomId) } returns participants
                every { blocks.isBlockedBetween(me, partner) } returns false
                every { repo.findRoom(roomId) } returns ChatRoom(id = roomId)
                every { repo.saveParticipant(any()) } answers { firstArg() }
                every { repo.increaseUnread(any(), any()) } returns Unit
                every { messages.nextSeq(roomId) } returns 1L
                every { messages.save(any()) } answers { firstArg<ChatMessage>().apply { id = "m1" } }

                service.send(me, roomId, ChatMessage.Type.TEXT, "돌아와", emptyList())

                verify { repo.saveParticipant(match { it.memberId == partner && it.leftAt == null }) }
            }
        }
    }

    Given("첨부 업로드 URL 을 발급할 때") {

        When("사진·영상·음성 contentType 이면") {
            Then("대응하는 Storage.Type 으로 presign 한다") {
                every { storage.presign(10L, "chat", Storage.Type.VIDEO, "a.mp4") } returns
                    Storage.PresignedResult("chat/a.mp4", "https://s3/put")

                service.presignUpload(10L, "a.mp4", "video/mp4")

                verify { storage.presign(10L, "chat", Storage.Type.VIDEO, "a.mp4") }
            }
        }

        When("그 외 contentType 이면") {
            Then("400 으로 거부하고 presign 을 부르지 않는다") {
                // 클라이언트가 준 contentType 을 그대로 믿으면 실행파일도 첨부로 올라간다
                shouldThrow<LanglezException> {
                    service.presignUpload(10L, "a.exe", "application/octet-stream")
                }.status.value() shouldBe 400

                verify(exactly = 0) { storage.presign(any(), any(), any(), any()) }
            }
        }
    }
})

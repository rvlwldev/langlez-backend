package com.langlez.chat.application

import com.langlez.chat.api.ChatResponse
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.chat.infrastructure.outbox.ChatOutBoxRepository
import com.langlez.core.FileStorage
import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.*
import java.time.Instant

class ChatServiceTest : BehaviorSpec({

    val chatRoomRepository = mockk<ChatRoomRepository>()
    val chatMessageRepository = mockk<ChatMessageRepository>()
    val memberRepository = mockk<MemberRepository>()
    val fileStorage = mockk<FileStorage>()
    val chatBroadcaster = mockk<ChatBroadcaster>(relaxed = true)
    val chatRoomCreator = mockk<ChatRoomCreator>()
    val chatOutBoxRepository = mockk<ChatOutBoxRepository>(relaxed = true)

    val service = ChatService(
        chatRoomRepository,
        chatMessageRepository,
        memberRepository,
        fileStorage,
        chatBroadcaster,
        chatRoomCreator,
        chatOutBoxRepository
    )

    afterEach {
        clearMocks(
            chatRoomRepository,
            chatMessageRepository,
            memberRepository,
            fileStorage,
            chatBroadcaster,
            chatRoomCreator,
            chatOutBoxRepository,
            answers = false
        )
    }

    fun createMember(id: Long, username: String = "user$id", nickname: String = "User $id") = Member(
        id = id,
        email = "$username@example.com",
        username = username,
        nickname = nickname,
        provider = Member.Provider.GOOGLE,
        providerId = "p$id",
        providerDisplayName = nickname
    )

    Given("getOrCreateRoom 호출 시") {
        val memberId = 1L
        val targetUsername = "target"
        val targetMember = createMember(2L, targetUsername)

        When("상대방이 존재하지 않으면") {
            every { memberRepository.findByUsername(targetUsername) } returns null

            Then("404 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.getOrCreateRoom(memberId, targetUsername)
                }.status shouldBe 404
                verify(exactly = 0) { chatRoomCreator.getOrCreateRoom(any(), any(), any()) }
            }
        }

        When("기존 방이 존재하면") {
            val existingRoom = ChatRoom(id = "room1", participantIds = listOf(1L, 2L).sorted())
            every { memberRepository.findByUsername(targetUsername) } returns targetMember
            every { chatRoomRepository.findByParticipants(1L, 2L) } returns existingRoom

            Then("기존 방을 반환하고 chatRoomCreator를 호출하지 않는다") {
                val result = service.getOrCreateRoom(memberId, targetUsername)
                result shouldBe existingRoom
                verify(exactly = 0) { chatRoomCreator.getOrCreateRoom(any(), any(), any()) }
            }
        }

        When("기존 방이 존재하지 않으면") {
            val createdRoom = ChatRoom(id = "room1", participantIds = listOf(1L, 2L).sorted())
            every { memberRepository.findByUsername(targetUsername) } returns targetMember
            every { chatRoomRepository.findByParticipants(1L, 2L) } returns null
            every { chatRoomCreator.getOrCreateRoom(memberId, 2L, 1L) } returns createdRoom

            Then("chatRoomCreator를 통해 방을 생성하고 반환한다") {
                val result = service.getOrCreateRoom(memberId, targetUsername)
                result shouldBe createdRoom
                verify(exactly = 1) { chatRoomCreator.getOrCreateRoom(memberId, 2L, 1L) }
            }
        }
    }

    Given("sendMessage 호출 시") {
        val memberId = 1L
        val roomId = "room1"

        When("방이 존재하지 않으면") {
            every { chatRoomRepository.findById(roomId) } returns null

            Then("404 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.sendMessage(memberId, roomId, ChatMessage.Type.TEXT, "hello", null)
                }.status shouldBe 404
            }
        }

        When("호출자가 방의 참여자가 아니면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(2L, 3L))
            every { chatRoomRepository.findById(roomId) } returns room

            Then("403 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.sendMessage(memberId, roomId, ChatMessage.Type.TEXT, "hello", null)
                }.status shouldBe 403
            }
        }

        When("정상적인 전송 요청이면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            val sender = createMember(1L)
            val savedMsg = ChatMessage(id = "msg1", roomId = roomId, senderId = memberId, type = ChatMessage.Type.TEXT, content = "hello")

            every { chatRoomRepository.findById(roomId) } returns room
            every { memberRepository.findById(memberId) } returns sender
            every { chatMessageRepository.save(any()) } returns savedMsg
            every { chatRoomRepository.save(any()) } returns room

            Then("메시지를 저장하고 방의 lastMessageAt을 갱신하며 브로드캐스트한다") {
                val result = service.sendMessage(memberId, roomId, ChatMessage.Type.TEXT, "hello", null)
                result shouldBe savedMsg
                room.lastMessagePreview shouldBe "hello"
                room.lastMessageAt shouldNotBe null
                verify { chatMessageRepository.save(any()) }
                verify { chatRoomRepository.save(room) }
                verify { chatBroadcaster.broadcastMessage(roomId, any()) }
            }
        }
    }

    Given("markAsRead 호출 시") {
        val memberId = 1L
        val roomId = "room1"

        When("참여자가 읽음 처리하면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            val member = createMember(1L, "user1")

            every { chatRoomRepository.findById(roomId) } returns room
            every { memberRepository.findById(memberId) } returns member
            every { chatRoomRepository.updateReadStatus(roomId, memberId, any()) } just runs

            Then("readStatus를 갱신하고 읽음 이벤트를 브로드캐스트한다") {
                service.markAsRead(memberId, roomId)
                verify { chatRoomRepository.updateReadStatus(roomId, memberId, any()) }
                verify { chatBroadcaster.broadcastRead(roomId, "user1", any()) }
            }
        }
    }

    Given("unreadCount 조회 시") {
        val memberId = 1L
        val roomId = "room1"

        When("읽은 시점 이후의 메시지 개수를 세면") {
            val room = ChatRoom(
                id = roomId,
                participantIds = listOf(1L, 2L),
                readStatus = mutableMapOf(1L to Instant.now())
            )
            val target = createMember(2L, "target")
            every { chatRoomRepository.findByParticipant(memberId, null, 20) } returns listOf(room)
            every { memberRepository.findByIds(listOf(1L, 2L)) } returns listOf(createMember(1L), target)
            every { chatMessageRepository.countUnread(roomId, memberId, any()) } returns 5

            Then("정확한 unreadCount가 포함된 방 목록을 반환한다") {
                val result = service.getRooms(memberId, null, 20)
                result.rooms shouldHaveSize 1
                result.rooms[0].unreadCount shouldBe 5
            }
        }
    }

    Given("deleteMessage 호출 시") {
        val memberId = 1L
        val roomId = "room1"
        val messageId = "msg1"

        When("정상 삭제 요청이면") {
            val message = ChatMessage(id = messageId, roomId = roomId, senderId = memberId, type = ChatMessage.Type.TEXT, content = "hello")
            every { chatMessageRepository.findById(messageId) } returns message
            every { chatMessageRepository.markDeleted(messageId, any()) } just runs

            Then("메시지를 삭제 처리하고 삭제 이벤트를 브로드캐스트한다") {
                service.deleteMessage(memberId, roomId, messageId)
                verify { chatMessageRepository.markDeleted(messageId, any()) }
                verify { chatBroadcaster.broadcastMessageDeleted(roomId, messageId) }
            }
        }

        When("본인이 작성하지 않은 메시지를 삭제하려고 하면") {
            val message = ChatMessage(id = messageId, roomId = roomId, senderId = 2L, type = ChatMessage.Type.TEXT, content = "hello")
            every { chatMessageRepository.findById(messageId) } returns message

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteMessage(memberId, roomId, messageId)
                }
                ex.status shouldBe 403
                ex.message shouldBe "chat.not-sender"
            }
        }

        When("이미 삭제된 메시지를 다시 삭제하려고 하면") {
            val message = ChatMessage(
                id = messageId,
                roomId = roomId,
                senderId = memberId,
                type = ChatMessage.Type.TEXT,
                content = "hello",
                deletedAt = Instant.now()
            )
            every { chatMessageRepository.findById(messageId) } returns message

            Then("409 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteMessage(memberId, roomId, messageId)
                }
                ex.status shouldBe 409
                ex.message shouldBe "chat.already-deleted"
            }
        }
    }

    Given("getMessages 호출 시") {
        val memberId = 1L
        val roomId = "room1"

        When("삭제된 메시지가 포함되어 있으면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            val deletedMsg = ChatMessage(
                id = "msg1",
                roomId = roomId,
                senderId = memberId,
                type = ChatMessage.Type.TEXT,
                content = "secret content",
                fileUrl = "http://example.com/file",
                deletedAt = Instant.now()
            )
            val normalMsg = ChatMessage(
                id = "msg2",
                roomId = roomId,
                senderId = memberId,
                type = ChatMessage.Type.TEXT,
                content = "hello",
                deletedAt = null
            )

            every { chatRoomRepository.findById(roomId) } returns room
            every { chatMessageRepository.findByRoom(roomId, null, 20) } returns listOf(deletedMsg, normalMsg)
            every { chatMessageRepository.findByIds(emptyList()) } returns emptyList()
            every { memberRepository.findByIds(listOf(memberId)) } returns listOf(createMember(memberId))

            Then("삭제된 메시지도 목록에 포함되지만 content/fileUrl은 null이고 deleted=true이다") {
                val result = service.getMessages(memberId, roomId, null, 20)
                result.messages shouldHaveSize 2

                val first = result.messages[0]
                first.id shouldBe "msg1"
                first.content shouldBe null
                first.fileUrl shouldBe null
                first.deleted shouldBe true

                val second = result.messages[1]
                second.id shouldBe "msg2"
                second.content shouldBe "hello"
                second.deleted shouldBe false
            }
        }
    }

    Given("sendMessage with replyToMessageId 호출 시") {
        val memberId = 1L
        val roomId = "room1"

        When("정상적인 답장 전송 요청이면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            val targetMsg = ChatMessage(id = "target1", roomId = roomId, senderId = 2L, type = ChatMessage.Type.TEXT, content = "target content")
            val savedMsg = ChatMessage(
                id = "msg2",
                roomId = roomId,
                senderId = memberId,
                type = ChatMessage.Type.TEXT,
                content = "reply content",
                replyToMessageId = "target1"
            )

            every { chatRoomRepository.findById(roomId) } returns room
            every { memberRepository.findById(memberId) } returns createMember(memberId, "user1")
            every { chatMessageRepository.findById("target1") } returns targetMsg
            every { chatMessageRepository.save(any()) } returns savedMsg
            every { chatRoomRepository.save(any()) } returns room
            every { memberRepository.findById(2L) } returns createMember(2L, "user2")

            Then("replyToMessageId를 저장하고 replyPreview가 채워진 summary를 반환한다") {
                val result = service.sendMessage(memberId, roomId, ChatMessage.Type.TEXT, "reply content", null, replyToMessageId = "target1")
                result shouldBe savedMsg

                val summary = service.toMessageSummary(savedMsg)
                summary.replyPreview shouldNotBe null
                summary.replyPreview?.messageId shouldBe "target1"
                summary.replyPreview?.senderUsername shouldBe "user2"
                summary.replyPreview?.contentPreview shouldBe "target content"
                summary.replyPreview?.deleted shouldBe false
            }
        }

        When("존재하지 않는 답장 대상이면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            every { chatRoomRepository.findById(roomId) } returns room
            every { chatMessageRepository.findById("invalid") } returns null

            Then("404 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.sendMessage(memberId, roomId, ChatMessage.Type.TEXT, "reply content", null, replyToMessageId = "invalid")
                }
                ex.status shouldBe 404
                ex.message shouldBe "chat.reply-target-not-found"
            }
        }

        When("삭제된 메시지에 답장하는 경우") {
            val targetMsg = ChatMessage(
                id = "target1",
                roomId = roomId,
                senderId = 2L,
                type = ChatMessage.Type.TEXT,
                content = "secret target",
                deletedAt = Instant.now()
            )
            val savedMsg = ChatMessage(
                id = "msg2",
                roomId = roomId,
                senderId = memberId,
                type = ChatMessage.Type.TEXT,
                content = "reply content",
                replyToMessageId = "target1"
            )

            every { chatMessageRepository.findById("target1") } returns targetMsg
            every { memberRepository.findById(memberId) } returns createMember(memberId, "user1")

            Then("replyPreview의 deleted=true이고 내용이 은닉된다") {
                val summary = service.toMessageSummary(savedMsg)
                summary.replyPreview shouldNotBe null
                summary.replyPreview?.deleted shouldBe true
                summary.replyPreview?.senderUsername shouldBe "알 수 없음"
                summary.replyPreview?.contentPreview shouldBe null
            }
        }
    }

    Given("reportUser 호출 시") {
        val reporterId = 1L
        val reportedUserId = 2L
        val roomId = "room1"

        When("정상적인 신고 요청이면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            val lastMsg = ChatMessage(id = "lastMsg1", roomId = roomId, senderId = reportedUserId, type = ChatMessage.Type.TEXT, content = "bad msg")

            every { chatRoomRepository.findById(roomId) } returns room
            every { chatMessageRepository.findLastMessage(roomId) } returns lastMsg
            every { chatOutBoxRepository.save(any(), any(), any(), any()) } returns mockk()

            Then("outbox에 CHAT_REPORT 이벤트를 저장한다") {
                service.reportUser(reporterId, roomId, reportedUserId, "inappropriate content")
                verify {
                    chatOutBoxRepository.save(
                        aggregateType = "CHAT_REPORT",
                        aggregateId = roomId,
                        eventName = "chat-user-reported",
                        payload = match {
                            it is ChatUserReportedEvent &&
                                    it.roomId == roomId &&
                                    it.reporterId == reporterId &&
                                    it.reportedUserId == reportedUserId &&
                                    it.reason == "inappropriate content" &&
                                    it.triggerMessageId == "lastMsg1"
                        }
                    )
                }
            }
        }

        When("신고 대상이 방 참가자가 아니면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(1L, 2L))
            every { chatRoomRepository.findById(roomId) } returns room

            Then("400 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.reportUser(reporterId, roomId, 3L, "reason")
                }
                ex.status shouldBe 400
                ex.message shouldBe "chat.reported-user-not-participant"
            }
        }

        When("신고자 본인이 방 참가자가 아니면") {
            val room = ChatRoom(id = roomId, participantIds = listOf(2L, 3L))
            every { chatRoomRepository.findById(roomId) } returns room

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.reportUser(reporterId, roomId, 2L, "reason")
                }
                ex.status shouldBe 403
                ex.message shouldBe "chat.room-forbidden"
            }
        }
    }
})

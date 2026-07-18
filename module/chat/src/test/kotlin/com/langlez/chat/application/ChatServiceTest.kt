package com.langlez.chat.application

import com.langlez.chat.api.ChatResponse
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
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

    val service = ChatService(
        chatRoomRepository,
        chatMessageRepository,
        memberRepository,
        fileStorage,
        chatBroadcaster,
        chatRoomCreator
    )

    afterEach {
        clearMocks(
            chatRoomRepository,
            chatMessageRepository,
            memberRepository,
            fileStorage,
            chatBroadcaster,
            chatRoomCreator,
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
})

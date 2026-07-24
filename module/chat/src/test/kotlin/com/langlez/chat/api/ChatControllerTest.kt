package com.langlez.chat.api

import com.langlez.chat.application.ChatService
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatRoom
import com.langlez.core.LanglezException
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.*
import java.time.Instant

class ChatControllerTest : BehaviorSpec({
    val service = mockk<ChatService>()
    val controller = ChatController(service)

    afterEach {
        clearMocks(service)
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

    Given("ChatController 가 주어졌을 때") {

        When("방을 생성하거나 조회할 때 (getOrCreateRoom)") {
            val room = ChatRoom(id = "room1", participantIds = listOf(1L, 2L))
            val summary = ChatResponse.RoomSummary(
                id = "room1",
                targetUsername = "user2",
                targetNickname = "User 2",
                lastMessageAt = null,
                lastMessagePreview = null,
                unreadCount = 0,
                createdAt = Instant.now()
            )
            every { service.getOrCreateRoom(1L, "user2") } returns room
            every { service.toRoomSummary(room, 1L) } returns summary

            Then("정상적으로 방 정보가 반환된다") {
                val result = controller.getOrCreateRoom(1L, "user2")
                result.id shouldBe "room1"
                result.targetUsername shouldBe "user2"
            }
        }

        When("방 목록을 조회할 때 (getRooms)") {
            val response = ChatResponse.RoomCursorList(
                nextCursor = null,
                rooms = listOf(
                    ChatResponse.RoomSummary(
                        id = "room1",
                        targetUsername = "user2",
                        targetNickname = "User 2",
                        lastMessageAt = null,
                        lastMessagePreview = null,
                        unreadCount = 0,
                        createdAt = Instant.now()
                    )
                )
            )
            every { service.getRooms(1L, null, 20) } returns response

            Then("방 목록을 올바르게 반환한다") {
                val result = controller.getRooms(1L, null, 20)
                result.rooms.size shouldBe 1
                result.rooms[0].id shouldBe "room1"
            }
        }

        When("메시지 목록을 조회할 때 (getMessages)") {
            val response = ChatResponse.MessageCursorList(
                nextCursor = null,
                messages = listOf(
                    ChatResponse.MessageSummary(
                        id = "msg1",
                        senderUsername = "user2",
                        type = ChatMessage.Type.TEXT,
                        content = "hello",
                        fileUrl = null,
                        createdAt = Instant.now()
                    )
                )
            )
            every { service.getMessages(1L, "room1", null, 20) } returns response

            Then("메시지 목록을 올바르게 반환한다") {
                val result = controller.getMessages(1L, "room1", null, 20)
                result.messages.size shouldBe 1
                result.messages[0].id shouldBe "msg1"
            }
        }

        When("미참여 유저가 메시지를 조회하려 할 때") {
            every { service.getMessages(3L, "room1", null, 20) } throws LanglezException(403, "chat.room-forbidden")

            Then("403 Forbidden 예외를 전파한다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getMessages(3L, "room1", null, 20)
                }
                ex.status shouldBe 403
                ex.message shouldBe "chat.room-forbidden"
            }
        }

        When("메시지를 전송할 때 (sendMessage)") {
            val sender = createMember(1L, "user1")
            val message = ChatMessage(id = "msg1", roomId = "room1", senderId = 1L, type = ChatMessage.Type.TEXT, content = "hello")
            val request = SendMessageRequest(type = ChatMessage.Type.TEXT, content = "hello")
            val summary = ChatResponse.MessageSummary(
                id = "msg1",
                senderUsername = "user1",
                type = ChatMessage.Type.TEXT,
                content = "hello",
                fileUrl = null,
                createdAt = message.createdAt
            )

            every { service.sendMessage(1L, "room1", ChatMessage.Type.TEXT, "hello", null, null) } returns message
            every { service.toMessageSummary(message) } returns summary

            Then("보낸 메시지 정보가 반환된다") {
                val result = controller.sendMessage(1L, "room1", request)
                result.id shouldBe "msg1"
                result.senderUsername shouldBe "user1"
                result.content shouldBe "hello"
            }
        }

        When("존재하지 않는 멤버가 메시지를 보낼 때") {
            val request = SendMessageRequest(type = ChatMessage.Type.TEXT, content = "hello")

            every { service.sendMessage(99L, "room1", ChatMessage.Type.TEXT, "hello", null, null) } throws LanglezException(404, "member.not-found")

            Then("404 Not Found 예외를 발생시킨다") {
                val ex = shouldThrow<LanglezException> {
                    controller.sendMessage(99L, "room1", request)
                }
                ex.status shouldBe 404
                ex.message shouldBe "member.not-found"
            }
        }

        When("메시지를 읽음 처리할 때 (markAsRead)") {
            every { service.markAsRead(1L, "room1") } just runs

            Then("서비스의 markAsRead 가 정상 호출된다") {
                controller.markAsRead(1L, "room1")
                verify { service.markAsRead(1L, "room1") }
            }
        }

        When("미참여 유저가 읽음 처리를 하려 할 때") {
            every { service.markAsRead(3L, "room1") } throws LanglezException(403, "chat.room-forbidden")

            Then("403 Forbidden 예외를 전파한다") {
                val ex = shouldThrow<LanglezException> {
                    controller.markAsRead(3L, "room1")
                }
                ex.status shouldBe 403
                ex.message shouldBe "chat.room-forbidden"
            }
        }

        When("정상적인 content type으로 업로드 URL을 요청할 때") {
            every { service.generateUploadUrl(any(), "image/jpeg") } returns "https://s3/upload/temp.jpg"

            Then("업로드 URL을 반환한다") {
                val result = controller.getUploadUrl("temp.jpg", "image/jpeg")
                result["uploadUrl"] shouldBe "https://s3/upload/temp.jpg"
            }
        }

        When("허용되지 않은 content type으로 업로드 URL을 요청할 때") {
            Then("400 Bad Request 예외를 발생시킨다") {
                val ex = shouldThrow<LanglezException> {
                    controller.getUploadUrl("temp.txt", "text/plain")
                }
                ex.status shouldBe 400
                ex.message shouldBe "file.unsupported-content-type"
            }
        }
    }
})

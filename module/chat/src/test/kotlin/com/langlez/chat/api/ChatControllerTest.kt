package com.langlez.chat.api

import com.langlez.attachment.contract.Storage
import com.langlez.chat.api.request.ChatMessageSendRequest
import com.langlez.chat.api.request.ChatReportRequest
import com.langlez.chat.api.request.ChatRoomCreateRequest
import com.langlez.chat.application.ChatMessageView
import com.langlez.chat.application.ChatService
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomSummary
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant

class ChatControllerTest : BehaviorSpec({

    val service = mockk<ChatService>()
    val storage = mockk<Storage>()
    val controller = ChatController(service)

    afterEach { clearMocks(service, storage, answers = false) }

    fun view(id: String = "m100", deleted: Boolean = false) = ChatMessageView(
        id = id,
        seq = 42L,
        roomId = 1L,
        senderId = 10L,
        type = ChatMessage.Type.TEXT,
        content = "hi",
        fileUrls = listOf("https://cdn.test/a.jpg"),
        createdAt = Instant.parse("2026-08-13T00:00:00Z"),
        deleted = deleted,
    )

    Given("방 생성 요청 시") {
        When("상대 id를 보내면") {
            every { service.getOrCreateRoom(10L, 20L) } returns ChatRoom(id = 1L)

            Then("방 응답을 돌려주고 인증된 회원 id로 서비스를 부른다") {
                val response = controller.createRoom(10L, ChatRoomCreateRequest(partnerId = 20L))

                response.id shouldBe 1L
                verify { service.getOrCreateRoom(10L, 20L) }
            }
        }
    }

    Given("방 목록 조회 시") {
        When("size를 과도하게 크게 요청하면") {
            every { service.listRooms(10L, 50, null) } returns emptyList()

            Then("상한으로 잘려 서비스에 전달된다") {
                controller.listRooms(10L, size = 1000, cursor = null)

                verify { service.listRooms(10L, 50, null) }
            }
        }

        When("방이 있으면") {
            val room = ChatRoom(id = 1L).apply { onMessage("hi", Instant.parse("2026-08-13T00:00:00Z")) }
            every { service.listRooms(10L, 20, null) } returns
                listOf(ChatRoomSummary(room = room, partnerId = 20L, unreadCount = 3L))

            Then("상대 id와 안 읽은 수가 담긴 요약을 돌려준다") {
                val response = controller.listRooms(10L, size = 20, cursor = null)

                response.single().partnerId shouldBe 20L
                response.single().unreadCount shouldBe 3L
                response.single().lastMessagePreview shouldBe "hi"
            }
        }
    }

    Given("메시지 목록 조회 시") {
        When("커서를 넘기면") {
            every { service.listMessages(10L, 1L, 20, 999L) } returns listOf(view())

            Then("커서 그대로 서비스에 전달하고 메시지를 돌려준다") {
                val response = controller.listMessages(10L, roomId = 1L, size = 20, cursor = 999L)

                response.single().id shouldBe "m100"
                verify { service.listMessages(10L, 1L, 20, 999L) }
            }
        }
    }

    Given("메시지 전송 시") {
        When("본문과 첨부 key를 보내면") {
            every { service.send(10L, 1L, ChatMessage.Type.TEXT, "hi", listOf("chat/a.jpg")) } returns view()

            Then("요청 값을 그대로 서비스에 넘기고 저장된 메시지를 돌려준다") {
                val request = ChatMessageSendRequest(
                    type = ChatMessage.Type.TEXT,
                    content = "hi",
                    keys = listOf("chat/a.jpg"),
                )

                val response = controller.sendMessage(10L, roomId = 1L, request = request)

                response.id shouldBe "m100"
                response.fileUrls shouldBe listOf("https://cdn.test/a.jpg")
                verify { service.send(10L, 1L, ChatMessage.Type.TEXT, "hi", listOf("chat/a.jpg")) }
            }
        }
    }

    Given("읽음 처리 시") {
        When("방 id를 보내면") {
            every { service.markRead(10L, 1L, any()) } returns Unit

            Then("서비스의 읽음 처리가 호출된다") {
                controller.readRoom(10L, roomId = 1L)

                verify { service.markRead(10L, 1L, any()) }
            }
        }
    }

    Given("방 나가기 시") {
        When("방 id를 보내면") {
            every { service.leaveRoom(10L, 1L) } returns Unit

            Then("서비스의 나가기가 호출된다") {
                controller.leaveRoom(10L, roomId = 1L)

                verify { service.leaveRoom(10L, 1L) }
            }
        }
    }

    Given("메시지 삭제 시") {
        When("메시지 id를 보내면") {
            every { service.deleteMessage(10L, "m100") } returns Unit

            Then("서비스의 삭제가 호출된다") {
                controller.deleteMessage(10L, messageId = "m100")

                verify { service.deleteMessage(10L, "m100") }
            }
        }
    }

    Given("신고 시") {
        When("사유와 문제 메시지를 보내면") {
            every { service.report(10L, 1L, "욕설", "m100") } returns Unit

            Then("서비스의 신고가 호출된다") {
                controller.report(10L, roomId = 1L, request = ChatReportRequest("욕설", "m100"))

                verify { service.report(10L, 1L, "욕설", "m100") }
            }
        }
    }

    Given("첨부 업로드 URL 발급 시") {
        When("filename 과 contentType 을 보내면") {
            val result = Storage.PresignedResult(key = "chat/2026-08-13/uuid_a.jpg", presigned = "https://s3/put?sig=1")
            every { service.presignUpload(10L, "a.jpg", "image/jpeg") } returns result

            Then("서비스에 위임하고 key 와 presigned 를 함께 돌려준다") {
                // contentType 검증은 비즈니스 규칙이라 서비스가 갖는다(ChatServiceTest 참고).
                controller.getUploadUrl(10L, filename = "a.jpg", contentType = "image/jpeg") shouldBe result
                verify { service.presignUpload(10L, "a.jpg", "image/jpeg") }
            }
        }
    }
})

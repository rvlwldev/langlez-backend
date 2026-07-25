package com.langlez.wavechat.application

import com.langlez.core.LanglezException
import com.langlez.wavechat.domain.WaveMessage
import com.langlez.wavechat.domain.WaveMessageRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class WaveChatServiceTest : BehaviorSpec({

    val waveMessageRepository = mockk<WaveMessageRepository>()
    val service = WaveChatService(waveMessageRepository)

    afterEach {
        clearMocks(waveMessageRepository, answers = false)
    }

    Given("sendMessage 호출 시") {
        val waveRoomId = 1L
        val senderId = 10L
        val content = "Test message content"

        When("유효한 파라미터가 전달되면") {
            val savedMessage = WaveMessage(
                waveRoomId = waveRoomId,
                senderId = senderId,
                content = content,
            )
            every { waveMessageRepository.save(any()) } returns savedMessage

            Then("메시지를 저장하고 저장된 엔티티를 반환한다") {
                val result = service.sendMessage(waveRoomId, senderId, content)

                result shouldBe savedMessage
                result.content shouldBe content
                verify(exactly = 1) { waveMessageRepository.save(any()) }
            }
        }
    }

    Given("getMessages 호출 시") {
        val waveRoomId = 1L

        When("메시지 목록 조회를 요청하면") {
            val messages = listOf(
                WaveMessage(waveRoomId = waveRoomId, senderId = 10L, content = "Msg 1"),
                WaveMessage(waveRoomId = waveRoomId, senderId = 20L, content = "Msg 2")
            )
            every { waveMessageRepository.findByRoom(waveRoomId, null, 20) } returns messages

            Then("삭제 여부 상관없이 순서대로 포함된 메시지 목록을 반환한다") {
                val result = service.getMessages(waveRoomId, null, 20)

                result shouldHaveSize 2
                verify(exactly = 1) { waveMessageRepository.findByRoom(waveRoomId, null, 20) }
            }
        }
    }

    Given("deleteMessage 호출 시") {
        val waveRoomId = 1L
        val senderId = 10L
        val messageId = 100L

        When("존재하지 않는 메시지 아이디로 삭제 시도하면") {
            every { waveMessageRepository.findById(messageId) } returns null

            Then("404 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteMessage(waveRoomId, senderId, messageId)
                }
                ex.status shouldBe 404
                ex.message shouldBe "wavechat.message-not-found"
            }
        }

        When("메시지의 waveRoomId와 요청한 waveRoomId가 일치하지 않으면") {
            val otherRoomMessage = WaveMessage(waveRoomId = 999L, senderId = senderId, content = "Msg")
            every { waveMessageRepository.findById(messageId) } returns otherRoomMessage

            Then("404 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteMessage(waveRoomId, senderId, messageId)
                }
                ex.status shouldBe 404
                ex.message shouldBe "wavechat.message-not-found"
            }
        }

        When("보낸 사람 본인이 아닌 다른 유저가 삭제 시도하면") {
            val message = WaveMessage(waveRoomId = waveRoomId, senderId = senderId, content = "Msg")
            every { waveMessageRepository.findById(messageId) } returns message

            Then("403 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> {
                    service.deleteMessage(waveRoomId, 999L, messageId)
                }
                ex.status shouldBe 403
                ex.message shouldBe "wavechat.forbidden"
            }
        }

        When("보낸 사람 본인이 올바른 방에서 삭제를 시도하면") {
            val message = WaveMessage(waveRoomId = waveRoomId, senderId = senderId, content = "Msg")
            every { waveMessageRepository.findById(messageId) } returns message

            Then("정상적으로 message.isDeleted()가 true가 된다") {
                service.deleteMessage(waveRoomId, senderId, messageId)

                message.isDeleted() shouldBe true
            }
        }
    }
})

package com.langlez.attachment.application

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

class AttachmentEventListenerTest : BehaviorSpec({

    val attachmentRepository = mockk<AttachmentRepository>(relaxed = true)
    val mapper = jacksonObjectMapper()

    val listener = AttachmentEventListener(
        attachmentRepository = attachmentRepository,
        mapper = mapper,
    )

    afterEach {
        clearMocks(attachmentRepository)
    }

    Given("EchoAttachmentUploaded 이벤트를 수신할 때") {
        When("정상적인 이벤트 JSON인 경우") {
            val payload = """
                {
                    "uploaderId": 1,
                    "postId": "post-100",
                    "attachments": [
                        {"fileType": "IMAGE", "storageKey": "path/to/img.png"}
                    ]
                }
            """.trimIndent()

            listener.onEchoAttachmentsUploaded(payload)

            Then("Attachment 엔티티로 변환되어 저장되어야 한다") {
                val slot = slot<List<Attachment>>()
                verify(exactly = 1) { attachmentRepository.saveAll(capture(slot)) }
                slot.captured.size shouldBe 1
                slot.captured[0].sourceType shouldBe Attachment.SourceType.ECHO
                slot.captured[0].fileType shouldBe Attachment.FileType.IMAGE
            }
        }

        When("유효하지 않은 fileType이 포함된 경우") {
            val payload = """
                {
                    "uploaderId": 1,
                    "postId": "post-100",
                    "attachments": [
                        {"fileType": "INVALID_TYPE", "storageKey": "path/to/img.png"}
                    ]
                }
            """.trimIndent()

            listener.onEchoAttachmentsUploaded(payload)

            Then("예외가 발생하지 않고 처리가 무시되어야 한다") {
                verify(exactly = 0) { attachmentRepository.saveAll(any()) }
            }
        }
    }

    Given("ChatAttachmentUploaded 이벤트를 수신할 때") {
        When("정상적인 이벤트 JSON인 경우") {
            val payload = """
                {
                    "uploaderId": 2,
                    "roomId": "room-200",
                    "fileType": "VIDEO",
                    "storageKey": "path/to/video.mp4"
                }
            """.trimIndent()

            listener.onChatAttachmentUploaded(payload)

            Then("Attachment 엔티티로 변환되어 저장되어야 한다") {
                val slot = slot<List<Attachment>>()
                verify(exactly = 1) { attachmentRepository.saveAll(capture(slot)) }
                slot.captured.size shouldBe 1
                slot.captured[0].sourceType shouldBe Attachment.SourceType.CHAT
                slot.captured[0].fileType shouldBe Attachment.FileType.VIDEO
            }
        }

        When("유효하지 않은 fileType인 경우") {
            val payload = """
                {
                    "uploaderId": 2,
                    "roomId": "room-200",
                    "fileType": "UNKNOWN_TYPE",
                    "storageKey": "path/to/video.mp4"
                }
            """.trimIndent()

            listener.onChatAttachmentUploaded(payload)

            Then("예외가 발생하지 않고 처리가 무시되어야 한다") {
                verify(exactly = 0) { attachmentRepository.saveAll(any()) }
            }
        }
    }
})

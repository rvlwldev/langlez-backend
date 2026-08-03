package com.langlez.attachment.api

import com.langlez.attachment.application.LocalAttachmentService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.clearMocks
import io.mockk.mockk
import io.mockk.verify
import java.io.ByteArrayInputStream

class AttachmentControllerTest : BehaviorSpec({

    val service = mockk<LocalAttachmentService>(relaxed = true)
    val controller = AttachmentController(service)

    afterEach { clearMocks(service, answers = false) }

    Given("파일 업로드 요청 시") {
        When("앞에 슬래시가 붙은 key와 Content-Type, body를 전달하면") {
            val body = ByteArrayInputStream("data".toByteArray())
            controller.upload("/chat/2026-08-03/uuid_photo.jpg", "image/jpeg", body)

            Then("LocalAttachmentService.store에 슬래시가 제거된 key와 Content-Type을 그대로 위임한다") {
                verify { service.store("chat/2026-08-03/uuid_photo.jpg", "image/jpeg", body) }
            }
        }
    }
})

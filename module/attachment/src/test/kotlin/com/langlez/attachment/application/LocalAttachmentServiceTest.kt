package com.langlez.attachment.application

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.core.Storage
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import java.io.ByteArrayInputStream
import java.io.File

class LocalAttachmentServiceTest : BehaviorSpec({

    val repo = mockk<AttachmentRepository>()
    val service = LocalAttachmentService(baseUrl = "http://localhost:8080/attachments", repo = repo)
    val root = File("attachments")

    afterEach { clearMocks(repo, answers = false) }
    afterSpec { root.deleteRecursively() }

    Given("첨부파일 업로드 URL 발급 시") {

        When("presign을 호출하면") {
            every { repo.save(any()) } answers { firstArg() }

            val result = service.presign(1L, "chat", Storage.Type.IMAGE, "photo.jpg")

            Then("chat/날짜/UUID_파일명 형태의 key와 로컬 업로드 URL을 반환하고 PENDING 상태로 저장한다") {
                result.key shouldContain "chat/"
                result.key shouldContain "_photo.jpg"
                result.presigned shouldBe "http://localhost:8080/attachments/${result.key}"
                verify { repo.save(match { it.status == Attachment.Status.PENDING && it.uploaderId == 1L }) }
            }
        }
    }

    Given("첨부파일 확인(attach) 시") {

        When("존재하지 않는 key로 요청하면") {
            every { repo.find("unknown") } returns null

            Then("attachment.not-found 예외가 발생한다") {
                shouldThrow<LanglezException> { service.attach("unknown", 100L) }
            }
        }

        When("DB엔 있지만 실제 파일이 업로드되지 않았으면") {
            val key = "chat/2026-08-03/missing.jpg"
            val attachment = Attachment.create(1L, Attachment.SourceType.CHAT, Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment

            Then("attachment.file-not-found 예외가 발생한다") {
                shouldThrow<LanglezException> { service.attach(key, 100L) }
            }
        }

        When("파일이 실제로 업로드되어 있으면") {
            val key = "chat/2026-08-03/${System.nanoTime()}_exists.jpg"
            service.store(key, ByteArrayInputStream("data".toByteArray()))

            val attachment = Attachment.create(1L, Attachment.SourceType.CHAT, Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment
            every { repo.save(any()) } answers { firstArg() }

            val url = service.attach(key, 555L)

            Then("ATTACHED 상태로 전환되고 조회 URL을 반환한다") {
                attachment.status shouldBe Attachment.Status.ATTACHED
                attachment.sourceId shouldBe "555"
                url shouldBe "http://localhost:8080/attachments/$key"
            }
        }
    }

    Given("store 호출 시") {
        When("경로 조작(path traversal) key로 요청하면") {
            Then("common.bad-request 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.store("../../etc/passwd", ByteArrayInputStream("x".toByteArray()))
                }
            }
        }
    }
})

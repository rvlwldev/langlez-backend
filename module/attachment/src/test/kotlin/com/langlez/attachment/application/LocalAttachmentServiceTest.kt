package com.langlez.attachment.application

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.attachment.contract.Storage
import com.langlez.exception.LanglezException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.mockk.*
import org.springframework.http.HttpStatus
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
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment

            Then("attachment.file-not-found 예외가 발생한다") {
                shouldThrow<LanglezException> { service.attach(key, 100L) }
            }
        }

        When("파일이 실제로 업로드되어 있으면") {
            val key = "chat/2026-08-03/${System.nanoTime()}_exists.jpg"
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment
            every { repo.save(any()) } answers { firstArg() }

            service.store(key, "image/jpeg", ByteArrayInputStream("data".toByteArray()))
            val url = service.attach(key, 555L)

            Then("ATTACHED 상태로 전환되고 조회 URL을 반환한다") {
                attachment.status shouldBe Attachment.Status.ATTACHED
                attachment.sourceId shouldBe "555"
                url shouldBe "http://localhost:8080/attachments/$key"
            }
        }

        When("이미 ATTACHED 상태인 첨부를 다시 attach하면") {
            val key = "chat/2026-08-03/${System.nanoTime()}_already-attached.jpg"
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment

            service.store(key, "image/jpeg", ByteArrayInputStream("data".toByteArray()))
            attachment.attach("1")

            Then("다른 sourceId 로의 변경은 common.bad-request 400 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.attach(key, 555L) }
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }

        When("sourceId 없이 이미 ATTACHED 상태인 첨부를 재시도하면 (C-07)") {
            val key = "echo/2026-08-03/${System.nanoTime()}_retry.jpg"
            val attachment = Attachment.create(1L, "echo", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment
            every { repo.save(any()) } answers { firstArg() }

            service.store(key, "image/jpeg", ByteArrayInputStream("data".toByteArray()))
            attachment.attach(null)

            Then("동일 key 로 재호출 시 400 에러 없이 멱등하게 URL 을 반환한다") {
                val url = service.attach(key, null)
                url shouldBe "http://localhost:8080/attachments/$key"
                attachment.status shouldBe Attachment.Status.ATTACHED
            }

            Then("기존 sourceId 가 null 일 때 새 sourceId 를 넘기면 갱신된다") {
                val url = service.attach(key, 555L)
                url shouldBe "http://localhost:8080/attachments/$key"
                attachment.sourceId shouldBe "555"
            }
        }
    }

    Given("store 호출 시") {

        When("presign된 적 없는 key로 요청하면") {
            every { repo.find("unknown-key") } returns null

            Then("attachment.not-found 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.store("unknown-key", "image/jpeg", ByteArrayInputStream("x".toByteArray()))
                }
            }
        }

        When("선언한 fileType과 다른 Content-Type으로 요청하면") {
            val key = "chat/2026-08-03/${System.nanoTime()}_video.mp4"
            every { repo.find(key) } returns Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)

            Then("attachment.invalid-content-type 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.store(key, "video/mp4", ByteArrayInputStream("x".toByteArray()))
                }
            }
        }

        When("경로 조작(path traversal) key로 요청하면") {
            val maliciousKey = "../../etc/passwd"
            every { repo.find(maliciousKey) } returns Attachment.create(1L, "chat", Attachment.Type.IMAGE, maliciousKey)

            Then("common.bad-request 예외가 발생한다") {
                shouldThrow<LanglezException> {
                    service.store(maliciousKey, "image/jpeg", ByteArrayInputStream("x".toByteArray()))
                }
            }
        }
    }
})

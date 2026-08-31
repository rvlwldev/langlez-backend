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
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectResponse
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI

class CloudAttachmentServiceTest : BehaviorSpec({

    val repo = mockk<AttachmentRepository>()
    val client = mockk<S3Client>()
    val presigner = mockk<S3Presigner>()

    val service = CloudAttachmentService(
        bucket = "langlez",
        baseUrl = "https://cdn.langlez.com",
        repo = repo,
        client = client,
        presigner = presigner,
    )

    afterEach { clearMocks(repo, client, presigner, answers = false) }

    Given("첨부파일 업로드 URL 발급 시") {

        When("presign을 호출하면") {
            val presigned = mockk<PresignedPutObjectRequest>()
            every { presigned.url() } returns URI("https://langlez.s3.amazonaws.com/presigned").toURL()
            every { presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presigned
            every { repo.save(any()) } answers { firstArg() }

            val result = service.presign(1L, "chat", Storage.Type.IMAGE, "photo.jpg")

            Then("S3 presigned PUT URL과 key를 반환하고 PENDING 상태로 저장한다") {
                result.presigned shouldBe "https://langlez.s3.amazonaws.com/presigned"
                result.key shouldContain "chat/"
                verify { repo.save(match { it.status == Attachment.Status.PENDING && it.source == "chat" }) }
            }
        }
    }

    Given("첨부파일 확인(attach) 시") {

        When("존재하지 않는 key로 요청하면") {
            every { repo.find("unknown") } returns null

            Then("attachment.not-found 예외가 발생한다") {
                shouldThrow<LanglezException> { service.attach("unknown", 1L) }
            }
        }

        When("DB엔 있지만 S3에 실제 업로드되지 않았으면") {
            val key = "chat/2026-08-03/missing.jpg"
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment
            every { client.headObject(any<HeadObjectRequest>()) } throws NoSuchKeyException.builder().build()

            Then("attachment.file-not-found 예외가 발생한다") {
                shouldThrow<LanglezException> { service.attach(key, 1L) }
            }
        }

        When("S3에 업로드된 Content-Type이 선언한 fileType과 다르면") {
            val key = "chat/2026-08-03/mismatch.jpg"
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment
            every { client.headObject(any<HeadObjectRequest>()) } returns HeadObjectResponse.builder().contentType("video/mp4").build()

            Then("attachment.invalid-content-type 예외가 발생한다") {
                shouldThrow<LanglezException> { service.attach(key, 1L) }
            }
        }

        When("S3에 실제로 업로드되어 있으면") {
            val key = "chat/2026-08-03/uuid_exists.jpg"
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key)
            every { repo.find(key) } returns attachment
            every { client.headObject(any<HeadObjectRequest>()) } returns HeadObjectResponse.builder().contentType("image/jpeg").build()
            every { repo.save(any()) } answers { firstArg() }

            val url = service.attach(key, 777L)

            Then("ATTACHED 상태로 전환되고 조회 URL을 반환한다") {
                attachment.status shouldBe Attachment.Status.ATTACHED
                attachment.sourceId shouldBe "777"
                url shouldBe "https://cdn.langlez.com/$key"
            }
        }

        When("이미 ATTACHED 상태인 첨부를 다시 attach하면") {
            val key = "chat/2026-08-03/uuid_already-attached.jpg"
            val attachment = Attachment.create(1L, "chat", Attachment.Type.IMAGE, key).apply { attach("1") }
            every { repo.find(key) } returns attachment
            every { client.headObject(any<HeadObjectRequest>()) } returns HeadObjectResponse.builder().contentType("image/jpeg").build()

            Then("common.bad-request 400 예외가 발생한다") {
                val ex = shouldThrow<LanglezException> { service.attach(key, 777L) }
                ex.status shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }
})

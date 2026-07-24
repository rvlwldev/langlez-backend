package com.langlez.file.application

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import io.mockk.*
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.net.URI

class S3FileStorageTest : BehaviorSpec({

    val s3Client = mockk<S3Client>()
    val s3Presigner = mockk<S3Presigner>()
    val bucket = "test-bucket"
    val storage = S3FileStorage(s3Client, s3Presigner, bucket)

    afterEach { clearMocks(s3Client, s3Presigner, answers = false) }

    Given("generateUploadUrl 호출 시") {

        When("정상적인 파일명, contentType, directory를 전달하면") {
            val presignedRequest = mockk<PresignedPutObjectRequest>()
            every { presignedRequest.url() } returns URI("https://test-bucket.s3.amazonaws.com/profiles/uuid_photo.jpg?presigned=true").toURL()
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presignedRequest

            val url = storage.generateUploadUrl("photo.jpg", "image/jpeg", "profiles")

            Then("S3 Presigned URL을 반환하고 S3Presigner가 호출된다") {
                url shouldStartWith "https://test-bucket.s3.amazonaws.com/"
                url shouldContain "profiles/"
                url shouldContain "photo.jpg"
                verify(exactly = 1) { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) }
            }
        }

        When("directory에 앞뒤 슬래시가 포함되어 있으면") {
            val presignedRequest = mockk<PresignedPutObjectRequest>()
            every { presignedRequest.url() } returns URI("https://test-bucket.s3.amazonaws.com/profiles/uuid_photo.jpg").toURL()
            every { s3Presigner.presignPutObject(any<PutObjectPresignRequest>()) } returns presignedRequest

            storage.generateUploadUrl("photo.jpg", "image/jpeg", "/profiles/")

            Then("슬래시가 정리된 key로 요청한다") {
                val slot = slot<PutObjectPresignRequest>()
                verify { s3Presigner.presignPutObject(capture(slot)) }
                val key = slot.captured.putObjectRequest().key()
                key shouldStartWith "profiles/"
            }
        }
    }

    Given("delete 호출 시") {

        When("정상적인 S3 URL을 전달하면") {
            every { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns DeleteObjectResponse.builder().build()

            storage.delete("https://test-bucket.s3.ap-northeast-2.amazonaws.com/profiles/uuid_photo.jpg")

            Then("올바른 key로 삭제 요청이 전송된다") {
                val slot = slot<DeleteObjectRequest>()
                verify(exactly = 1) { s3Client.deleteObject(capture(slot)) }
                slot.captured.key() shouldContain "profiles/uuid_photo.jpg"
                slot.captured.bucket() shouldContain bucket
            }
        }

        When("잘못된 형식의 URL을 전달하면") {
            Then("IllegalArgumentException이 발생한다") {
                io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                    storage.delete("http://invalid url with spaces")
                }
            }
        }
    }
})

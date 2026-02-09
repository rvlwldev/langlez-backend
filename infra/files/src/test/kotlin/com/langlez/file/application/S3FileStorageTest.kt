package com.langlez.file.application

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.mock.web.MockMultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectResponse

class S3FileStorageTest :
        BehaviorSpec({
            val s3Client = mockk<S3Client>()
            val bucket = "test-bucket"
            val region = "ap-northeast-2"
            val storage = S3FileStorage(s3Client, bucket, region)

            Given("S3 파일 저장소가 준비된 상태에서") {
                val folder = "profile"
                val originalFileName = "avatar.png"
                val contentType = "image/png"
                val content = "image data".toByteArray()
                val multipartFile =
                        MockMultipartFile("file", originalFileName, contentType, content)

                every { s3Client.putObject(any<PutObjectRequest>(), any<RequestBody>()) } returns
                        PutObjectResponse.builder().build()

                When("파일 업로드를 요청하면") {
                    val fileUrl = storage.upload(multipartFile, folder)

                    Then("S3Client의 putObject가 호출되어야 한다") {
                        val requestSlot = slot<PutObjectRequest>()
                        verify(exactly = 1) {
                            s3Client.putObject(capture(requestSlot), any<RequestBody>())
                        }

                        val capturedRequest = requestSlot.captured
                        capturedRequest.bucket() shouldBe bucket
                        capturedRequest.key() shouldBe
                                "$folder/${fileUrl.substringAfterLast("/")}" // UUID 포함 확인 어려우므로 뒷부분
                        // 비교
                        capturedRequest.contentType() shouldBe contentType
                    }

                    Then("반환된 URL이 올바른 S3 형식을 가져야 한다") {
                        // https://test-bucket.s3.ap-northeast-2.amazonaws.com/profile/{uuid}_avatar.png
                        fileUrl shouldStartWith "https://$bucket.s3.$region.amazonaws.com/$folder/"
                        fileUrl.endsWith(originalFileName) shouldBe true
                    }
                }
            }

            Given("삭제할 파일 URL이 주어졌을 때") {
                val folder = "profile"
                val fileName = "uuid_avatar.png"
                val fileUrl = "https://$bucket.s3.$region.amazonaws.com/$folder/$fileName"

                every { s3Client.deleteObject(any<DeleteObjectRequest>()) } returns mockk()

                When("파일 삭제를 요청하면") {
                    storage.delete(fileUrl)

                    Then("URL에서 키를 추출하여 deleteObject를 호출해야 한다") {
                        val requestSlot = slot<DeleteObjectRequest>()
                        verify(exactly = 1) { s3Client.deleteObject(capture(requestSlot)) }

                        val capturedRequest = requestSlot.captured
                        capturedRequest.bucket() shouldBe bucket
                        // infra/files/src/main/kotlin/com/langlez/file/application/S3FileStorage.kt:36
                        // val key = fileUrl.substringAfter(".com/")
                        // URL: ...amazonaws.com/profile/uuid_avatar.png -> Key:
                        // profile/uuid_avatar.png
                        capturedRequest.key() shouldBe "$folder/$fileName"
                    }
                }
            }
        })

package com.langlez.file.application

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldStartWith
import java.io.ByteArrayInputStream
import java.io.File

class LocalFileStorageTest : BehaviorSpec({

    val storage = LocalFileStorage(port = 8080)
    val testRootPath = "attachments"

    afterSpec {
        File(testRootPath).deleteRecursively()
    }

    Given("generateUploadUrl 호출 시") {

        When("정상적인 파일명, contentType, directory를 전달하면") {
            val url = storage.generateUploadUrl("photo.jpg", "image/jpeg", "profiles")

            Then("로컬 컨트롤러 URL을 반환한다") {
                url shouldStartWith "http://localhost:8080/api/files/upload?"
                url shouldContain "filename=photo.jpg"
                url shouldContain "contentType=image/jpeg"
                url shouldContain "directory=profiles"
            }
        }

        When("directory에 앞뒤 슬래시가 포함되어 있으면") {
            val url = storage.generateUploadUrl("photo.jpg", "image/jpeg", "/profiles/")

            Then("슬래시를 제거한 URL을 반환한다") {
                url shouldContain "directory=profiles"
            }
        }

        When("파일명에 특수문자가 포함되어 있으면") {
            val url = storage.generateUploadUrl("사진 파일.jpg", "image/jpeg", "profiles")

            Then("URL 인코딩된 파일명이 포함된다") {
                url shouldContain "filename="
                url shouldNotContain " "
            }
        }
    }

    Given("store 호출 시") {

        When("정상적인 InputStream과 경로를 전달하면") {
            val content = "test file content".toByteArray()
            val inputStream = ByteArrayInputStream(content)
            val result = storage.store(inputStream, "test.txt", "uploads")

            Then("파일이 저장되고 경로를 반환한다") {
                result shouldStartWith "/attachments/uploads/"
                result shouldContain "test.txt"
                File(result.removePrefix("/")).exists() shouldBe true
            }
        }

        When("빈 directory를 전달하면") {
            val content = "test".toByteArray()
            val inputStream = ByteArrayInputStream(content)
            val result = storage.store(inputStream, "root-file.txt", "")

            Then("루트 attachments 폴더에 저장된다") {
                result shouldStartWith "/attachments/"
                result shouldContain "root-file.txt"
            }
        }

        When("directory에 path traversal 공격 문자(..)가 포함되면") {
            val content = "malicious".toByteArray()
            val inputStream = ByteArrayInputStream(content)
            val result = storage.store(inputStream, "hack.txt", "../../etc")

            Then("..이 제거되고 안전한 경로에 저장된다") {
                result shouldStartWith "/attachments/"
                result shouldNotContain ".."
            }
        }

        When("파일명에 path traversal 문자(..)가 포함되면") {
            Then("..이 제거된 파일명으로 저장된다") {
                val content = "malicious".toByteArray()
                val inputStream = ByteArrayInputStream(content)
                val result = storage.store(inputStream, "../../../etc/passwd", "uploads")
                result shouldNotContain ".."
            }
        }
    }

    Given("delete 호출 시") {

        When("존재하는 파일 URL을 전달하면") {
            val content = "to-delete".toByteArray()
            val url = storage.store(ByteArrayInputStream(content), "delete-me.txt", "uploads")
            storage.delete(url)

            Then("파일이 삭제된다") {
                File(url.removePrefix("/")).exists() shouldBe false
            }
        }

        When("존재하지 않는 파일 URL을 전달하면") {
            Then("에러 없이 무시된다") {
                storage.delete("/attachments/uploads/nonexistent.txt")
            }
        }

        When("path traversal이 포함된 URL을 전달하면") {
            Then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    storage.delete("/../../etc/passwd")
                }
            }
        }
    }
})

private infix fun String.shouldNotContain(substr: String) {
    this.contains(substr) shouldBe false
}

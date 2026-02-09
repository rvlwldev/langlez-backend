package com.langlez.file.application

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import java.io.File
import org.springframework.mock.web.MockMultipartFile

class LocalFileStorageTest : BehaviorSpec({
    val storage = LocalFileStorage()
    val testFolder = "test-uploads"
    val originalFileName = "test-image.jpg"
    val content = "dummy content".toByteArray()

    Given("로컬 파일 저장소가 준비된 상태에서") {
        val multipartFile = MockMultipartFile("file", originalFileName, "image/jpeg", content)

        When("파일 업로드를 요청하면") {
            val fileUrl = storage.upload(multipartFile, testFolder)

            Then("URL이 attachments 경로로 시작해야 한다") {
                fileUrl shouldStartWith "/attachments/$testFolder/"
            }

            Then("실제 파일이 해당 경로에 존재해야 한다") {
                val filePath = fileUrl.removePrefix("/")
                val file = File(filePath)

                file.exists() shouldBe true
                file.readBytes() shouldBe content
            }
        }
    }

    Given("이미 저장된 파일이 있을 때") {
        val multipartFile = MockMultipartFile("file", "full_path_test.jpg", "image/jpeg", content)
        val fileUrl = storage.upload(multipartFile, testFolder)

        When("파일 삭제를 요청하면") {
            storage.delete(fileUrl)

            Then("파일이 시스템에서 사라져야 한다") {
                val filePath = fileUrl.removePrefix("/")
                File(filePath).exists() shouldBe false
            }
        }
    }

    Given("폴더 지정 없이 파일 업로드를 요청할 때") {
        val multipartFile = MockMultipartFile("file", "root_file.jpg", "image/jpeg", content)

        When("업로드를 요청하면") {
            val fileUrl = storage.upload(multipartFile) // folder 생략

            Then("URL이 attachments 바로 아래 경로여야 한다") {
                fileUrl shouldStartWith "/attachments/"
                fileUrl.substringAfter("/attachments/").contains("/") shouldBe false
            }

            Then("파일이 attachments 루트에 존재해야 한다") {
                val filePath = fileUrl.removePrefix("/")
                File(filePath).exists() shouldBe true
            }
        }
    }

    afterSpec {
        val rootName = "attachments"
        File("$rootName/$testFolder").deleteRecursively()

        val root = File(rootName)
        if (root.exists() && root.listFiles()?.isEmpty() == true)
            root.delete()
    }
})

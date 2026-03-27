package com.langlez.file.api

import com.langlez.file.application.LocalFileStorage
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.*
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.io.InputStream

class FileUploadControllerTest : BehaviorSpec({

    val storage = mockk<LocalFileStorage>()
    val controller = FileUploadController(storage)
    val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(controller).build()

    afterEach { clearMocks(storage, answers = false) }

    Given("POST /api/files/upload") {

        When("정상적인 파일 업로드 요청이 들어오면") {
            every { storage.store(any<InputStream>(), eq("photo.jpg"), eq("image/jpeg"), eq("profiles")) } returns "/attachments/profiles/uuid_photo.jpg"

            val result = mockMvc.post("/api/files/upload") {
                param("filename", "photo.jpg")
                param("contentType", "image/jpeg")
                param("directory", "profiles")
                contentType = MediaType.APPLICATION_OCTET_STREAM
                content = "fake-image-data".toByteArray()
            }

            Then("200 OK와 저장된 URL을 반환하고 store가 호출된다") {
                result.andExpect {
                    status { isOk() }
                    jsonPath("$.url") { value("/attachments/profiles/uuid_photo.jpg") }
                }
                verify(exactly = 1) { storage.store(any(), "photo.jpg", "image/jpeg", "profiles") }
            }
        }

        When("filename 파라미터가 누락되면") {
            val result = mockMvc.post("/api/files/upload") {
                param("contentType", "image/jpeg")
                param("directory", "profiles")
                contentType = MediaType.APPLICATION_OCTET_STREAM
                content = "data".toByteArray()
            }

            Then("400 Bad Request를 반환한다") {
                result.andExpect { status { isBadRequest() } }
            }
        }

        When("directory 파라미터가 누락되면") {
            val result = mockMvc.post("/api/files/upload") {
                param("filename", "photo.jpg")
                param("contentType", "image/jpeg")
                contentType = MediaType.APPLICATION_OCTET_STREAM
                content = "data".toByteArray()
            }

            Then("400 Bad Request를 반환한다") {
                result.andExpect { status { isBadRequest() } }
            }
        }

        When("빈 바디로 요청하면") {
            every { storage.store(any<InputStream>(), eq("empty.jpg"), eq("image/jpeg"), eq("uploads")) } returns "/attachments/uploads/uuid_empty.jpg"

            val result = mockMvc.post("/api/files/upload") {
                param("filename", "empty.jpg")
                param("contentType", "image/jpeg")
                param("directory", "uploads")
                contentType = MediaType.APPLICATION_OCTET_STREAM
                content = ByteArray(0)
            }

            Then("요청 자체는 성공한다 (0바이트 검증은 클라이언트 책임)") {
                result.andExpect { status { isOk() } }
            }
        }
    }
})

package com.langlez.attachment.api

import com.langlez.attachment.application.LocalAttachmentService
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.io.InputStream

/** 로컬 프로필 전용: S3 Presigned PUT 대신 클라이언트가 이 서버로 직접 업로드 */
@RestController
@RequestMapping("/attachments")
@Profile("!production")
class AttachmentController(private val local: LocalAttachmentService) {

    @PutMapping("/{*key}")
    fun upload(
        @PathVariable key: String,
        @RequestHeader(HttpHeaders.CONTENT_TYPE, required = false, defaultValue = "") contentType: String,
        body: InputStream,
    ) {
        local.store(key.removePrefix("/"), contentType, body)
    }
}

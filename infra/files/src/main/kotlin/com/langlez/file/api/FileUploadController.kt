package com.langlez.file.api

import com.langlez.file.application.LocalFileStorage
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.io.InputStream

@RestController
@RequestMapping("/api/files")
@Profile("!production")
class FileUploadController(private val storage: LocalFileStorage) {

    @PostMapping("/upload", consumes = [MediaType.APPLICATION_OCTET_STREAM_VALUE])
    fun upload(
        @RequestParam filename: String,
        @RequestParam contentType: String,
        @RequestParam directory: String,
        body: InputStream,
    ): Map<String, String> {
        val url = storage.store(body, filename, contentType, directory)
        return mapOf("url" to url)
    }
}

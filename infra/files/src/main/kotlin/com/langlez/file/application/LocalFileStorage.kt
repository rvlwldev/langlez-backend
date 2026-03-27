package com.langlez.file.application

import com.langlez.core.FileStorage
import java.io.File
import java.io.InputStream
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("!production")
class LocalFileStorage(
    @param:Value("\${server.port:8080}") private val port: Int,
) : FileStorage {
    private val rootPath = "attachments"

    companion object {
        private val ALLOWED_MIME_PREFIXES = listOf("image/", "video/", "audio/")
    }

    override fun generateUploadUrl(filename: String, contentType: String, directory: String): String {
        val encodedFilename = URLEncoder.encode(filename, StandardCharsets.UTF_8)
        val encodedDirectory = URLEncoder.encode(directory.trim('/'), StandardCharsets.UTF_8)
        return "http://localhost:$port/api/files/upload?filename=$encodedFilename&contentType=$contentType&directory=$encodedDirectory"
    }

    fun store(inputStream: InputStream, filename: String, contentType: String, directory: String): String {
        if (ALLOWED_MIME_PREFIXES.none { contentType.startsWith(it) })
            throw IllegalArgumentException("지원하지 않는 파일 타입입니다: $contentType")

        val sanitizedDir = directory.replace("..", "").replace("\\", "/").trim('/')

        val dir = if (sanitizedDir.isBlank()) File(rootPath)
        else File("$rootPath/$sanitizedDir").also {
            require(it.canonicalPath.startsWith(File(rootPath).canonicalPath)) { "잘못된 경로" }
            if (!it.exists()) it.mkdirs()
        }

        val sanitizedFilename = filename.replace("..", "").replace("/", "").replace("\\", "")
        val storedName = "${UUID.randomUUID()}_$sanitizedFilename"
        val targetFile = File(dir, storedName)
        inputStream.use { it.copyTo(targetFile.outputStream()) }

        return if (sanitizedDir.isBlank()) "/$rootPath/$storedName"
        else "/$rootPath/$sanitizedDir/$storedName"
    }

    override fun delete(fileUrl: String) {
        val file = File(fileUrl.removePrefix("/"))
        require(file.canonicalPath.startsWith(File(rootPath).canonicalPath)) { "잘못된 경로" }
        if (file.exists()) file.delete()
    }
}

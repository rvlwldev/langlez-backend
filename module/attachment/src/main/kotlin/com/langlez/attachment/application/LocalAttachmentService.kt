package com.langlez.attachment.application

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.core.Storage
import com.langlez.exception.LanglezException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import java.io.File
import java.io.InputStream

@Service
@Profile("!production")
class LocalAttachmentService(
    @param:Value($$"${storage.base-url}") private val baseUrl: String,
    private val repo: AttachmentRepository,
) : Storage {
    private val root = File("attachments")

    override fun presign(id: Long, source: String, type: Storage.Type, filename: String): Storage.PresignedResult {
        val fileType = Attachment.Type.valueOf(type.name)
        val key = Attachment.buildKey(source, filename)

        repo.save(Attachment.create(id, source, fileType, key))

        return Storage.PresignedResult(key, "$baseUrl/$key")
    }

    override fun attach(key: String, sourceId: Long?): String {
        val attachment = repo.find(key)
            ?: throw LanglezException(HttpStatus.NOT_FOUND, "attachment.not-found")

        if (!resolve(key).exists())
            throw LanglezException(HttpStatus.NOT_FOUND, "attachment.file-not-found")

        attachment.attach(sourceId?.toString())
        repo.save(attachment)

        return "$baseUrl/$key"
    }

    /** AttachmentController가 로컬 업로드(PUT) 받을 때 호출. Storage 인터페이스엔 없는 로컬 전용 메서드. */
    fun store(key: String, input: InputStream) {
        val target = resolve(key)
        target.parentFile.mkdirs()
        input.use { it.copyTo(target.outputStream()) }
    }

    private fun resolve(key: String): File {
        val file = File(root, key)
        if (!file.canonicalPath.startsWith(root.canonicalPath))
            throw LanglezException(HttpStatus.BAD_REQUEST, "common.bad-request")
        return file
    }
}

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

        attachment.attachOrThrow(sourceId?.toString())
        repo.save(attachment)

        return "$baseUrl/$key"
    }

    /** AttachmentController가 로컬 업로드(PUT) 받을 때 호출. Storage 인터페이스엔 없는 로컬 전용 메서드. */
    fun store(key: String, contentType: String, input: InputStream) {
        val attachment = repo.find(key)
            ?: throw LanglezException(HttpStatus.NOT_FOUND, "attachment.not-found")

        if (attachment.status != Attachment.Status.PENDING)
            throw LanglezException(HttpStatus.BAD_REQUEST, "common.bad-request")

        if (!contentType.startsWith(attachment.type.mime))
            throw LanglezException(HttpStatus.BAD_REQUEST, "attachment.invalid-content-type")

        val target = resolve(key)
        target.parentFile.mkdirs()
        // 출력 스트림도 use 로 감싼다. 안 닫으면 fd 가 새고 버퍼가 안 flush 돼 파일이 잘린다.
        input.use { source -> target.outputStream().use(source::copyTo) }
    }

    private fun resolve(key: String): File {
        val file = File(root, key)
        // separator 를 붙여야 한다. 안 그러면 "<root>-evil" 같은 형제 경로가 통과한다.
        if (!file.canonicalPath.startsWith(root.canonicalPath + File.separator))
            throw LanglezException(HttpStatus.BAD_REQUEST, "common.bad-request")
        return file
    }
}

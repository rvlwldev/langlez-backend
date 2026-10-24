package com.langlez.attachment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

data class FileSpec(val contentType: String, val size: Long)

data class IssueBatchRequest(val source: String, val type: AttachmentType, val files: List<FileSpec>)

data class IssuedBatch(val batchId: UUID, val uploads: List<UploadTicket>)

data class CompletedBatch(val urls: List<String>)

private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

private fun notFound(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)

@Service
class AttachmentService(
    private val repository: AttachmentRepository,
    private val storage: ObjectStorage,
    private val properties: AttachmentProperties,
) {

    fun issue(uploaderId: UUID, request: IssueBatchRequest): IssuedBatch {
        if (request.source !in properties.sources) badRequest("허용되지 않은 source 입니다")
        val type = request.type
        if (request.files.size !in 1..type.maxFiles) badRequest("$type 은 파일 1~${type.maxFiles}개만 올릴 수 있습니다")

        val formats = request.files.map { file ->
            val format = ContentFormat.find(file.contentType)
            if (format == null || format.type != type) badRequest("$type 에 허용되지 않은 contentType 입니다: ${file.contentType}")
            if (file.size !in 1..type.maxSizeBytes) badRequest("$type 파일 크기는 1~${type.maxSizeBytes} 바이트여야 합니다")
            format
        }

        val batchId = UUID.randomUUID()
        val now = Instant.now()
        val datePath = LocalDate.now(ZoneOffset.UTC)
        val attachments = request.files.mapIndexed { position, file ->
            val format = formats[position]
            Attachment(
                uploaderId = uploaderId,
                source = request.source,
                type = type,
                batchId = batchId,
                position = position,
                objectKey = "${request.source.lowercase()}/$datePath/${UUID.randomUUID()}.${format.extension}",
                contentType = format.contentType,
                sizeBytes = file.size,
                createdAt = now,
            )
        }
        repository.saveAll(attachments)

        val uploads = attachments.map { storage.presignPut(it.objectKey, it.contentType, it.sizeBytes, type.presignDuration) }
        return IssuedBatch(batchId, uploads)
    }

    fun complete(uploaderId: UUID, batchId: UUID): CompletedBatch {
        val attachments = repository.findByBatchIdAndUploaderIdOrderByPosition(batchId, uploaderId)
        if (attachments.isEmpty()) notFound("존재하지 않는 batch 입니다")

        if (attachments.any { it.status == AttachmentStatus.PENDING }) {
            attachments.forEach(::verify)
            repository.markUploaded(batchId, Instant.now())
        }
        return CompletedBatch(attachments.map { properties.publicUrl(it.objectKey) })
    }

    private fun verify(attachment: Attachment) {
        val stored = storage.head(attachment.objectKey) ?: notFound("업로드되지 않은 파일이 있습니다")
        val storedContentType = stored.contentType.substringBefore(';').trim()
        if (!storedContentType.equals(attachment.contentType, ignoreCase = true)) badRequest("contentType 이 발급 때와 다릅니다")
        if (stored.size != attachment.sizeBytes) badRequest("파일 크기가 발급 때와 다릅니다")

        val format = ContentFormat.find(attachment.contentType) ?: badRequest("지원하지 않는 contentType 입니다")
        if (!format.signature.matches(storage.readHead(attachment.objectKey))) badRequest("파일 내용이 contentType 과 맞지 않습니다")
    }
}

package com.langlez.attachment.application

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.core.Storage
import com.langlez.exception.LanglezException
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.NoSuchKeyException
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration

@Service
@Profile("production")
class CloudAttachmentService(
    @param:Value($$"${storage.bucket}") private val bucket: String,
    @param:Value($$"${storage.base-url}") private val baseUrl: String,
    private val repo: AttachmentRepository,
    private val client: S3Client,
    private val presigner: S3Presigner,
) : Storage {

    override fun presign(id: Long, source: String, type: Storage.Type, filename: String): Storage.PresignedResult {
        val fileType = Attachment.Type.valueOf(type.name)
        val key = Attachment.buildKey(source, filename)

        repo.save(Attachment.create(id, source, fileType, key))

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .putObjectRequest(putObjectRequest)
            .signatureDuration(PRESIGN_EXPIRATION)
            .build()

        val presigned = presigner.presignPutObject(presignRequest).url().toString()

        return Storage.PresignedResult(key, presigned)
    }

    override fun attach(key: String, sourceId: Long?): String {
        val attachment = repo.find(key)
            ?: throw LanglezException(HttpStatus.NOT_FOUND, "attachment.not-found")

        val head = try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build())
        } catch (e: NoSuchKeyException) {
            throw LanglezException(HttpStatus.NOT_FOUND, "attachment.file-not-found", e)
        }

        if (head.contentType() == null || !head.contentType().startsWith(attachment.type.mime))
            throw LanglezException(HttpStatus.BAD_REQUEST, "attachment.invalid-content-type")

        attachment.attach(sourceId?.toString())
        repo.save(attachment)

        return "$baseUrl/$key"
    }

    companion object {
        private val PRESIGN_EXPIRATION: Duration = Duration.ofMinutes(15)
    }
}

package com.langlez.attachment

import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.server.ResponseStatusException
import software.amazon.awssdk.core.sync.ResponseTransformer
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.time.Duration

data class UploadTicket(val key: String, val url: String, val headers: Map<String, String>)

data class StoredObject(val contentType: String, val size: Long, val etag: String)

@Component
class ObjectStorage(
    private val s3Client: S3Client,
    private val presigner: S3Presigner,
    private val properties: AttachmentProperties,
) {

    fun presignPut(key: String, contentType: String, size: Long, validFor: Duration): UploadTicket {
        val request = PutObjectRequest.builder()
            .bucket(properties.bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(size)
            .build()
        val presigned = presigner.presignPutObject { it.signatureDuration(validFor).putObjectRequest(request) }
        return UploadTicket(
            key = key,
            url = presigned.url().toString(),
            headers = mapOf("Content-Type" to contentType, "Content-Length" to size.toString()),
        )
    }

    fun head(key: String): StoredObject? =
        try {
            val response = s3Client.headObject { it.bucket(properties.bucket).key(key) }
            StoredObject(response.contentType().orEmpty(), response.contentLength(), response.eTag())
        } catch (e: S3Exception) {
            if (e.statusCode() == 404) null else throw e
        }

    fun readHead(key: String, etag: String): ByteArray =
        whenChanged {
            s3Client.getObject(
                { it.bucket(properties.bucket).key(key).ifMatch(etag).range("bytes=0-${SIGNATURE_BYTES - 1}") },
                ResponseTransformer.toBytes(),
            ).asByteArray()
        }

    fun copy(sourceKey: String, destinationKey: String, etag: String) {
        whenChanged {
            s3Client.copyObject {
                it.sourceBucket(properties.bucket).sourceKey(sourceKey)
                    .destinationBucket(properties.bucket).destinationKey(destinationKey)
                    .copySourceIfMatch(etag)
            }
        }
    }

    private fun <T> whenChanged(call: () -> T): T =
        try {
            call()
        } catch (e: S3Exception) {
            if (e.statusCode() == 412) throw ResponseStatusException(HttpStatus.CONFLICT, "검증한 뒤 파일이 바뀌었습니다") else throw e
        }
}

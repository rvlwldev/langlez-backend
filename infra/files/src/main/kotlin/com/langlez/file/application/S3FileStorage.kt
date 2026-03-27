package com.langlez.file.application

import com.langlez.core.FileStorage
import java.time.Duration
import java.util.UUID
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest

@Component
@Profile("production")
internal class S3FileStorage(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    @param:Value("\${cloud.aws.s3.bucket}") private val bucket: String,
) : FileStorage {

    override fun generateUploadUrl(filename: String, contentType: String, directory: String): String {
        val key = "${directory.trim('/')}/${UUID.randomUUID()}_$filename"

        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .build()

        val presignRequest = PutObjectPresignRequest.builder()
            .putObjectRequest(putObjectRequest)
            .signatureDuration(Duration.ofMinutes(5))
            .build()

        return s3Presigner.presignPutObject(presignRequest).url().toString()
    }

    override fun delete(fileUrl: String) {
        val key = fileUrl.substringAfter(".com/")
        val deleteRequest = DeleteObjectRequest.builder().bucket(bucket).key(key).build()
        s3Client.deleteObject(deleteRequest)
    }
}

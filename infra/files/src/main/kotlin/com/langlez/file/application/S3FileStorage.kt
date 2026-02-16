package com.langlez.file.application

import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

@Component
@Profile("prod")
internal class S3FileStorage(
    private val s3Client: S3Client,
    @param:Value("\${cloud.aws.s3.bucket}") private val bucket: String,
    @param:Value("\${cloud.aws.s3.region}") private val region: String,
) : FileStorage {

    override suspend fun upload(file: MultipartFile, folder: String?): String = withContext(Dispatchers.IO) {
        val uuidName = "${UUID.randomUUID()}_${file.originalFilename}"
        val key = if (folder.isNullOrBlank()) uuidName else "$folder/$uuidName"

        val request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(file.contentType)
            .build()

        s3Client.putObject(request, RequestBody.fromInputStream(file.inputStream, file.size))

        "https://$bucket.s3.$region.amazonaws.com/$key"
    }

    override suspend fun delete(fileUrl: String) {
        withContext(Dispatchers.IO) {
            val key = fileUrl.substringAfter(".com/")
            val deleteRequest = DeleteObjectRequest.builder().bucket(bucket).key(key).build()

            s3Client.deleteObject(deleteRequest)
        }
    }
}

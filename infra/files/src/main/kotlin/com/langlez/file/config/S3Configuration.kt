package com.langlez.file.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
@Profile("production")
class S3Configuration {

    @Bean
    fun s3Client(
            @Value("\${cloud.aws.credentials.access-key}") accessKey: String,
            @Value("\${cloud.aws.credentials.secret-key}") secretKey: String,
            @Value("\${cloud.aws.region.static}") region: String,
    ): S3Client {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()
    }

    @Bean
    fun s3Presigner(
            @Value("\${cloud.aws.credentials.access-key}") accessKey: String,
            @Value("\${cloud.aws.credentials.secret-key}") secretKey: String,
            @Value("\${cloud.aws.region.static}") region: String,
    ): S3Presigner {
        val credentials = AwsBasicCredentials.create(accessKey, secretKey)
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build()
    }
}

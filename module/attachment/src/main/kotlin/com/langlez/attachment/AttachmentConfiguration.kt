package com.langlez.attachment

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner

@Configuration
class AttachmentConfiguration {

    @Bean
    @Profile("production")
    fun s3Client(
        @Value($$"${storage.access-key}") accessKey: String,
        @Value($$"${storage.secret-key}") secretKey: String,
        @Value($$"${storage.region}") region: String,
    ): S3Client =
        S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider(accessKey, secretKey))
            .build()

    @Bean
    @Profile("production")
    fun s3Presigner(
        @Value($$"${storage.access-key}") accessKey: String,
        @Value($$"${storage.secret-key}") secretKey: String,
        @Value($$"${storage.region}") region: String,
    ): S3Presigner =
        S3Presigner.builder()
            .region(Region.of(region))
            .credentialsProvider(credentialsProvider(accessKey, secretKey))
            .build()

    /** 로컬 프로필: S3 대신 프로젝트 루트 attachments 디렉터리를 /attachments 경로로 정적 서빙 (조회용) */
    @Bean
    @Profile("!production")
    fun localAttachmentResourceConfigurer(): WebMvcConfigurer = object : WebMvcConfigurer {
        override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
            registry.addResourceHandler("/attachments/**")
                .addResourceLocations("file:attachments/")
        }
    }

    private fun credentialsProvider(accessKey: String, secretKey: String): StaticCredentialsProvider =
        StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
}

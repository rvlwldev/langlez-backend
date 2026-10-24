package com.langlez.attachment

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("langlez.attachment")
data class AttachmentProperties(
    val endpoint: String,
    val region: String,
    val bucket: String,
    val accessKey: String,
    val secretKey: String,
    val publicBaseUrl: String,
    val pathStyle: Boolean = false,
    val sources: List<String> = listOf("PROFILE"),
) {
    fun publicUrl(objectKey: String): String = "${publicBaseUrl.trimEnd('/')}/$objectKey"

    override fun toString(): String =
        "AttachmentProperties(endpoint=$endpoint, region=$region, bucket=$bucket, publicBaseUrl=$publicBaseUrl, " +
            "pathStyle=$pathStyle, sources=$sources)"
}

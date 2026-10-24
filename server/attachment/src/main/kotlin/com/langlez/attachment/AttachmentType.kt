package com.langlez.attachment

import java.time.Duration

private const val MEGABYTE = 1024L * 1024

enum class AttachmentType(
    val maxFiles: Int,
    val maxSizeBytes: Long,
    val presignDuration: Duration,
) {
    IMAGE(maxFiles = 10, maxSizeBytes = 10 * MEGABYTE, presignDuration = Duration.ofMinutes(15)),
    VIDEO(maxFiles = 10, maxSizeBytes = 500 * MEGABYTE, presignDuration = Duration.ofHours(1)),
    AUDIO(maxFiles = 1, maxSizeBytes = 10 * MEGABYTE, presignDuration = Duration.ofMinutes(15)),
}

enum class AttachmentStatus { PENDING, UPLOADED }

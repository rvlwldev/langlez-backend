package com.langlez.profile.api

import com.langlez.profile.domain.ProfileImage
import java.time.Instant

data class ImageResponse(
    val url: String,
    val sequence: Long,
    val represent: Boolean,
    val createdAt: Instant,
) {
    constructor(image: ProfileImage) : this(
        url = image.url,
        sequence = image.sequence,
        represent = image.represent,
        createdAt = image.createdAt,
    )
}

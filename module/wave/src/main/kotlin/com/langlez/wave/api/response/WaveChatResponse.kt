package com.langlez.wave.api.response

import com.langlez.wave.domain.WaveChat
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class WaveChatResponse(
    @field:Schema(description = "방 id") val roomId: Long,
    @field:Schema(description = "보낸 사람 회원 id") val senderId: Long,
    @field:Schema(description = "본문") val content: String,
    @field:Schema(description = "보낸 시각") val sentAt: Instant,
) {
    constructor(chat: WaveChat) : this(
        roomId = chat.roomId,
        senderId = chat.senderId,
        content = chat.content,
        sentAt = chat.sentAt,
    )
}

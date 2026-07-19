package com.langlez.wavechat.api

import com.langlez.wavechat.domain.WaveMessage
import java.time.Instant

data class WaveChatResponse(
    val id: Long,
    val waveRoomId: Long,
    val senderId: Long,
    val content: String?,
    val deleted: Boolean,
    val createdAt: Instant,
) {
    companion object {
        fun from(message: WaveMessage): WaveChatResponse {
            val isDeleted = message.isDeleted()
            return WaveChatResponse(
                id = message.id,
                waveRoomId = message.waveRoomId,
                senderId = message.senderId,
                content = if (isDeleted) null else message.content,
                deleted = isDeleted,
                createdAt = message.createdAt,
            )
        }
    }
}

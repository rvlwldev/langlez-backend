package com.langlez.chat.api.response

import com.langlez.chat.domain.ChatRoom
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class ChatRoomResponse(
    @field:Schema(description = "방 id") val id: Long,
    @field:Schema(description = "마지막 메시지 시각", nullable = true) val lastMessageAt: Instant?,
    @field:Schema(description = "마지막 메시지 미리보기", nullable = true) val lastMessagePreview: String?,
    @field:Schema(description = "방 생성일시") val createdAt: Instant,
) {
    constructor(room: ChatRoom) : this(
        id = room.id,
        lastMessageAt = room.lastMessageAt,
        lastMessagePreview = room.lastMessagePreview,
        createdAt = room.createdAt,
    )
}

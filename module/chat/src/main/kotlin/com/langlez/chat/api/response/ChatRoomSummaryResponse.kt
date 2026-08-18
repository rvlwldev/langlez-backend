package com.langlez.chat.api.response

import com.langlez.chat.domain.ChatRoomSummary
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/** 목록용 응답. 방 자체 정보에 상대 id와 안 읽은 수가 얹힌다. */
data class ChatRoomSummaryResponse(
    @field:Schema(description = "방 id") val roomId: Long,
    @field:Schema(description = "대화 상대 회원 id") val partnerId: Long,
    @field:Schema(description = "안 읽은 메시지 수") val unreadCount: Long,
    @field:Schema(description = "마지막 메시지 시각", nullable = true) val lastMessageAt: Instant?,
    @field:Schema(description = "마지막 메시지 미리보기", nullable = true) val lastMessagePreview: String?,
) {
    constructor(summary: ChatRoomSummary) : this(
        roomId = summary.room.id,
        partnerId = summary.partnerId,
        unreadCount = summary.unreadCount,
        lastMessageAt = summary.room.lastMessageAt,
        lastMessagePreview = summary.room.lastMessagePreview,
    )
}

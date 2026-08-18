package com.langlez.wave.api.response

import com.langlez.wave.domain.WaveRoom
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

data class WaveRoomResponse(
    @field:Schema(description = "방 id") val id: Long,
    @field:Schema(description = "방장 회원 id") val broadcasterId: Long,
    @field:Schema(description = "방 제목") val title: String,
    @field:Schema(description = "최대 인원") val maxParticipants: Int,
    @field:Schema(description = "현재 인원") val participantCount: Int,
    @field:Schema(description = "시작 시각") val startedAt: Instant,
) {
    constructor(room: WaveRoom, participantCount: Int) : this(
        id = room.id,
        broadcasterId = room.broadcasterId,
        title = room.title,
        maxParticipants = room.maxParticipants,
        participantCount = participantCount,
        startedAt = room.startedAt,
    )
}

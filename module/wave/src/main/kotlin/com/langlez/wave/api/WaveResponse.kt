package com.langlez.wave.api

import java.time.Instant

class WaveResponse {
    data class RoomSummary(
        val id: Long,
        val broadcasterUsername: String,
        val broadcasterNickname: String,
        val title: String,
        val maxParticipants: Int,
        val startedAt: Instant,
        val endedAt: Instant?,
        val viewerCount: Long
    )

    data class RoomCursorList(
        val nextCursor: Long?,
        val rooms: List<RoomSummary>
    )
}

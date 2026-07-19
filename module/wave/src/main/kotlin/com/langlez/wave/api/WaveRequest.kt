package com.langlez.wave.api

class WaveRequest {
    data class StartLive(
        val title: String,
        val maxParticipants: Int
    )

    data class UpdateTitle(
        val title: String
    )

    data class MuteMember(
        val targetMemberId: Long
    )

    data class KickMember(
        val targetMemberId: Long
    )
}

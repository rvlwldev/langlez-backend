package com.langlez.wave.api.request

import com.langlez.wave.domain.WaveRoom
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class WaveRoomCreateRequest(
    @field:Schema(description = "방 제목")
    @field:NotBlank(message = "wave.title.invalid")
    @field:Size(max = 255, message = "wave.title.invalid")
    val title: String,

    // 범위는 엔티티가 정본이다. 여기서 숫자를 또 적으면 두 곳이 어긋난다.
    @field:Schema(description = "최대 인원", defaultValue = "4")
    @field:Min(value = WaveRoom.MIN_PARTICIPANTS.toLong(), message = "wave.max-participants.invalid")
    @field:Max(value = WaveRoom.MAX_PARTICIPANTS.toLong(), message = "wave.max-participants.invalid")
    val maxParticipants: Int = WaveRoom.MIN_PARTICIPANTS,
)

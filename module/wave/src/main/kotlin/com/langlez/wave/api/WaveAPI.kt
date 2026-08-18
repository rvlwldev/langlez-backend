package com.langlez.wave.api

import com.langlez.wave.api.request.WaveChatSendRequest
import com.langlez.wave.api.request.WaveRoomCreateRequest
import com.langlez.wave.api.response.WaveChatResponse
import com.langlez.wave.api.response.WaveRoomResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag

@Tag(name = "Wave", description = "단체 음성방 API. 방 안의 채팅은 저장되지 않고 방이 끝나면 사라진다.")
interface WaveAPI {

    @Operation(summary = "음성방 개설", description = "연 사람이 방장이자 첫 참여자가 된다.")
    fun createRoom(memberId: Long, request: WaveRoomCreateRequest): WaveRoomResponse

    @Operation(
        summary = "진행 중인 음성방 목록",
        description = "id 내림차순(최신순). cursor 는 직전 페이지 마지막 방의 id 를 넣는다.",
    )
    fun listRooms(
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 방의 id") cursor: Long?,
    ): List<WaveRoomResponse>

    @Operation(summary = "입장", description = "정원이 차 있으면 409. 이미 참여 중이면 그대로 통과한다.")
    fun join(memberId: Long, @Parameter(description = "방 id") roomId: Long)

    @Operation(summary = "퇴장", description = "마지막 참여자가 나가면 방이 자동으로 닫힌다.")
    fun leave(memberId: Long, @Parameter(description = "방 id") roomId: Long)

    @Operation(summary = "방 종료", description = "방장만 종료할 수 있다. 오간 대화도 함께 사라진다.")
    fun end(memberId: Long, @Parameter(description = "방 id") roomId: Long)

    @Operation(
        summary = "채팅 전송",
        description = "참여자만 보낼 수 있다. 저장되지 않고 최근 몇 개만 방에 남는다.",
    )
    fun sendChat(
        memberId: Long,
        @Parameter(description = "방 id") roomId: Long,
        request: WaveChatSendRequest,
    ): WaveChatResponse

    @Operation(
        summary = "최근 대화",
        description = "늦게 들어온 사람이 흐름을 따라잡는 용도. 오래된 순으로 최근 몇 개만 남아 있다.",
    )
    fun listChats(memberId: Long, @Parameter(description = "방 id") roomId: Long): List<WaveChatResponse>
}

package com.langlez.wave.api

import com.langlez.annotation.MemberId
import com.langlez.wave.api.request.WaveChatSendRequest
import com.langlez.wave.api.request.WaveRoomCreateRequest
import com.langlez.wave.api.response.WaveChatResponse
import com.langlez.wave.api.response.WaveRoomResponse
import com.langlez.wave.application.WaveService
import com.langlez.wave.domain.WaveSessionRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/waves")
class WaveController(
    private val service: WaveService,
    private val sessions: WaveSessionRepository,
) : WaveAPI {

    @PostMapping
    override fun createRoom(
        @MemberId memberId: Long,
        @RequestBody @Valid request: WaveRoomCreateRequest,
    ): WaveRoomResponse =
        WaveRoomResponse(service.createRoom(memberId, request.title, request.maxParticipants), participantCount = 1)

    @GetMapping
    override fun listRooms(
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<WaveRoomResponse> =
        service.listOpenRooms(size.coerceIn(1, MAX_SIZE), cursor)
            // ponytail: 방 하나당 레디스 왕복 1회. 목록이 한 페이지(최대 50)뿐이라 그대로 둔다.
            // 더 커지면 참여자 수를 방 목록과 함께 파이프라인으로 묶는다.
            .map { WaveRoomResponse(it, sessions.participants(it.id).size) }

    @PostMapping("/{roomId}/participants")
    @ResponseStatus(NO_CONTENT)
    override fun join(@MemberId memberId: Long, @PathVariable roomId: Long) {
        service.join(roomId, memberId)
    }

    @DeleteMapping("/{roomId}/participants/me")
    @ResponseStatus(NO_CONTENT)
    override fun leave(@MemberId memberId: Long, @PathVariable roomId: Long) {
        service.leave(roomId, memberId)
    }

    @DeleteMapping("/{roomId}")
    @ResponseStatus(NO_CONTENT)
    override fun end(@MemberId memberId: Long, @PathVariable roomId: Long) {
        service.end(roomId, memberId)
    }

    @PostMapping("/{roomId}/chats")
    override fun sendChat(
        @MemberId memberId: Long,
        @PathVariable roomId: Long,
        @RequestBody @Valid request: WaveChatSendRequest,
    ): WaveChatResponse = WaveChatResponse(service.chat(roomId, memberId, request.content))

    @GetMapping("/{roomId}/chats")
    override fun listChats(@MemberId memberId: Long, @PathVariable roomId: Long): List<WaveChatResponse> =
        service.recentChats(roomId, memberId).map(::WaveChatResponse)

    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 전체 방 목록을 긁어갈 수 있다.
        private const val MAX_SIZE = 50
    }
}

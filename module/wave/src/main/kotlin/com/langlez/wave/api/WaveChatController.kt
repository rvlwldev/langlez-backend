package com.langlez.wave.api

import com.langlez.core.LanglezException
import com.langlez.security.web.MemberID
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.WaveViewerTracker
import com.langlez.wavechat.api.WaveChatResponse
import com.langlez.wavechat.application.WaveChatService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Wave 채팅 메시지 조회/삭제 REST API. module:wavechat은 저장만 담당하고,
 * 방 존재 여부/밴 여부 같은 권한 체크는 WaveRoomRepository/WaveViewerTracker를
 * 가진 이 모듈(module:wave)이 담당한다 — module:wavechat이 module:wave를
 * 역참조하면 순환 의존이 생기기 때문에 이렇게 나눴다.
 */
@RestController
@RequestMapping("/api/v1/waves/{roomId}/messages")
class WaveChatController(
    private val waveChatService: WaveChatService,
    private val waveRoomRepository: WaveRoomRepository,
    private val viewerTracker: WaveViewerTracker,
) {

    @GetMapping
    fun getMessages(
        @MemberID memberId: Long,
        @PathVariable roomId: Long,
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int,
    ): List<WaveChatResponse> {
        checkRoomAccess(roomId, memberId)
        return waveChatService.getMessages(roomId, cursor, size).map { WaveChatResponse.from(it) }
    }

    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMessage(
        @MemberID memberId: Long,
        @PathVariable roomId: Long,
        @PathVariable messageId: Long,
    ) {
        checkRoomAccess(roomId, memberId)
        waveChatService.deleteMessage(roomId, memberId, messageId)
    }

    private fun checkRoomAccess(roomId: Long, memberId: Long) {
        waveRoomRepository.findById(roomId) ?: throw LanglezException(404, "wave.room-not-found")
        if (viewerTracker.isBanned(roomId, memberId)) {
            throw LanglezException(403, "wave.banned-user")
        }
    }
}

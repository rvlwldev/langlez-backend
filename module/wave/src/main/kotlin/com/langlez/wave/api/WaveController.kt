package com.langlez.wave.api

import com.langlez.security.web.MemberID
import com.langlez.wave.application.WaveService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/waves")
class WaveController(
    private val service: WaveService,
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun startLive(
        @MemberID memberId: Long,
        @RequestBody request: WaveRequest.StartLive
    ): WaveResponse.RoomSummary {
        val room = service.startLive(memberId, request.title, request.maxParticipants)
        return service.toRoomSummary(room)
    }

    @PatchMapping("/{roomId}/end")
    fun endLive(
        @MemberID memberId: Long,
        @PathVariable roomId: Long
    ): WaveResponse.RoomSummary {
        val room = service.endLive(memberId, roomId)
        return service.toRoomSummary(room)
    }

    @PatchMapping("/{roomId}/title")
    fun updateTitle(
        @MemberID memberId: Long,
        @PathVariable roomId: Long,
        @RequestBody request: WaveRequest.UpdateTitle
    ): WaveResponse.RoomSummary {
        val room = service.updateTitle(memberId, roomId, request.title)
        return service.toRoomSummary(room)
    }

    @PostMapping("/{roomId}/mute")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun muteMember(
        @MemberID memberId: Long,
        @PathVariable roomId: Long,
        @RequestBody request: WaveRequest.MuteMember
    ) {
        service.muteMember(memberId, roomId, request.targetMemberId)
    }

    @PostMapping("/{roomId}/kick")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun kickMember(
        @MemberID memberId: Long,
        @PathVariable roomId: Long,
        @RequestBody request: WaveRequest.KickMember
    ) {
        service.kickMember(memberId, roomId, request.targetMemberId)
    }

    @GetMapping
    fun getActiveRooms(
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): WaveResponse.RoomCursorList {
        val boundedSize = size.coerceIn(1, WaveService.MAX_PAGE_SIZE)
        val rooms = service.getActiveRooms(cursor, boundedSize)
        val summaries = rooms.map { service.toRoomSummary(it) }
        val nextCursor = if (rooms.size == boundedSize) rooms.lastOrNull()?.id else null
        return WaveResponse.RoomCursorList(nextCursor, summaries)
    }

    @GetMapping("/{roomId}")
    fun getRoom(@PathVariable roomId: Long): WaveResponse.RoomSummary {
        val room = service.getRoom(roomId)
        return service.toRoomSummary(room)
    }
}

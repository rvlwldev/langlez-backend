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
    fun startLive(@MemberID memberId: Long): WaveResponse.RoomSummary {
        val room = service.startLive(memberId)
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

    @GetMapping
    fun getActiveRooms(
        @RequestParam(required = false) cursor: Long?,
        @RequestParam(defaultValue = "20") size: Int
    ): WaveResponse.RoomCursorList {
        val rooms = service.getActiveRooms(cursor, size)
        val summaries = rooms.map { service.toRoomSummary(it) }
        val nextCursor = if (rooms.size == size) rooms.lastOrNull()?.id else null
        return WaveResponse.RoomCursorList(nextCursor, summaries)
    }

    @GetMapping("/{roomId}")
    fun getRoom(@PathVariable roomId: Long): WaveResponse.RoomSummary {
        val room = service.getRoom(roomId)
        return service.toRoomSummary(room)
    }
}

package com.langlez.chat.api

import com.langlez.chat.application.ChatService
import com.langlez.chat.domain.ChatMessage
import com.langlez.core.LanglezException
import com.langlez.security.web.MemberID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(
    private val service: ChatService,
) {

    @PostMapping("/rooms/{username}")
    @ResponseStatus(HttpStatus.CREATED)
    fun getOrCreateRoom(
        @MemberID memberId: Long,
        @PathVariable username: String
    ): ChatResponse.RoomSummary {
        val room = service.getOrCreateRoom(memberId, username)
        return service.toRoomSummary(room, memberId)
    }

    @GetMapping("/rooms")
    fun getRooms(
        @MemberID memberId: Long,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ChatResponse.RoomCursorList {
        return service.getRooms(memberId, cursor, size)
    }

    @GetMapping("/rooms/{roomId}/messages")
    fun getMessages(
        @MemberID memberId: Long,
        @PathVariable roomId: String,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "20") size: Int
    ): ChatResponse.MessageCursorList {
        return service.getMessages(memberId, roomId, cursor, size)
    }

    @PostMapping("/rooms/{roomId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    fun sendMessage(
        @MemberID memberId: Long,
        @PathVariable roomId: String,
        @RequestBody request: SendMessageRequest
    ): ChatResponse.MessageSummary {
        val message = service.sendMessage(
            memberId = memberId,
            roomId = roomId,
            type = request.type,
            content = request.content,
            fileUrl = request.fileUrl,
            replyToMessageId = request.replyToMessageId
        )
        return service.toMessageSummary(message)
    }

    @DeleteMapping("/rooms/{roomId}/messages/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMessage(
        @MemberID memberId: Long,
        @PathVariable roomId: String,
        @PathVariable messageId: String
    ) {
        service.deleteMessage(memberId, roomId, messageId)
    }

    @PostMapping("/rooms/{roomId}/report")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reportUser(
        @MemberID memberId: Long,
        @PathVariable roomId: String,
        @RequestBody request: ChatReportRequest
    ) {
        service.reportUser(
            reporterId = memberId,
            roomId = roomId,
            reportedUserId = request.reportedUserId,
            reason = request.reason
        )
    }

    @PatchMapping("/rooms/{roomId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAsRead(
        @MemberID memberId: Long,
        @PathVariable roomId: String
    ) {
        service.markAsRead(memberId, roomId)
    }

    @GetMapping("/upload-url")
    fun getUploadUrl(
        @RequestParam(required = false) filename: String?,
        @RequestParam contentType: String
    ): Map<String, String> {
        val allowedMimePrefixes = listOf("image/", "video/", "audio/")
        if (allowedMimePrefixes.none { contentType.startsWith(it) }) {
            throw LanglezException(400, "file.unsupported-content-type")
        }
        val resolvedFilename = filename ?: run {
            val ext = when (contentType) {
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/gif" -> "gif"
                "video/mp4" -> "mp4"
                "audio/mp3", "audio/mpeg" -> "mp3"
                "audio/m4a" -> "m4a"
                "audio/wav" -> "wav"
                else -> contentType.substringAfter("/").takeIf { it.isNotBlank() } ?: "bin"
            }
            "${UUID.randomUUID()}.$ext"
        }
        val uploadUrl = service.generateUploadUrl(resolvedFilename, contentType)
        return mapOf("uploadUrl" to uploadUrl)
    }
}

data class SendMessageRequest(
    val type: ChatMessage.Type,
    val content: String? = null,
    val fileUrl: String? = null,
    val replyToMessageId: String? = null
)

data class ChatReportRequest(
    val reportedUserId: Long,
    val reason: String
)

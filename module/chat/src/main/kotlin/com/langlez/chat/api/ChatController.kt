package com.langlez.chat.api

import com.langlez.annotation.MemberId
import com.langlez.chat.api.request.ChatMessageSendRequest
import com.langlez.chat.api.request.ChatReportRequest
import com.langlez.chat.api.request.ChatRoomCreateRequest
import com.langlez.chat.api.response.ChatMessageResponse
import com.langlez.chat.api.response.ChatRoomResponse
import com.langlez.chat.api.response.ChatRoomSummaryResponse
import com.langlez.chat.application.ChatService
import com.langlez.attachment.contract.Storage
import com.langlez.exception.LanglezException
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus.BAD_REQUEST
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
import java.time.Instant

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(private val service: ChatService) : ChatAPI {

    @PostMapping("/rooms")
    override fun createRoom(
        @MemberId memberId: Long,
        @RequestBody @Valid request: ChatRoomCreateRequest,
    ): ChatRoomResponse = ChatRoomResponse(service.getOrCreateRoom(memberId, request.partnerId))

    @GetMapping("/rooms")
    override fun listRooms(
        @MemberId memberId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) cursor: Instant?,
    ): List<ChatRoomSummaryResponse> =
        service.listRooms(memberId, size.coerceIn(1, MAX_ROOM_SIZE), cursor).map(::ChatRoomSummaryResponse)

    @GetMapping("/rooms/{roomId}/messages")
    override fun listMessages(
        @MemberId memberId: Long,
        @PathVariable roomId: Long,
        @RequestParam(defaultValue = "$DEFAULT_SIZE") size: Int,
        @RequestParam(required = false) cursor: Long?,
    ): List<ChatMessageResponse> =
        service.listMessages(memberId, roomId, size.coerceIn(1, MAX_MESSAGE_SIZE), cursor).map(::ChatMessageResponse)

    @PostMapping("/rooms/{roomId}/messages")
    override fun sendMessage(
        @MemberId memberId: Long,
        @PathVariable roomId: Long,
        @RequestBody @Valid request: ChatMessageSendRequest,
    ): ChatMessageResponse =
        ChatMessageResponse(service.send(memberId, roomId, request.type, request.content, request.keys))

    @PostMapping("/rooms/{roomId}/read")
    @ResponseStatus(NO_CONTENT)
    override fun readRoom(@MemberId memberId: Long, @PathVariable roomId: Long) {
        service.markRead(memberId, roomId)
    }

    @DeleteMapping("/rooms/{roomId}")
    @ResponseStatus(NO_CONTENT)
    override fun leaveRoom(@MemberId memberId: Long, @PathVariable roomId: Long) {
        service.leaveRoom(memberId, roomId)
    }

    @DeleteMapping("/messages/{messageId}")
    @ResponseStatus(NO_CONTENT)
    override fun deleteMessage(@MemberId memberId: Long, @PathVariable messageId: String) {
        service.deleteMessage(memberId, messageId)
    }

    @PostMapping("/rooms/{roomId}/report")
    @ResponseStatus(NO_CONTENT)
    override fun report(
        @MemberId memberId: Long,
        @PathVariable roomId: Long,
        @RequestBody @Valid request: ChatReportRequest,
    ) {
        service.report(memberId, roomId, request.reason, request.triggerMessageId)
    }

    /**
     * key 를 함께 내려줘야 클라이언트가 서명 붙은 PUT URL 대신 key 로 전송할 수 있다.
     * URL 을 그대로 받으면 외부 주소를 첨부로 심을 수 있다.
     */
    @GetMapping("/upload-url")
    override fun getUploadUrl(
        @MemberId memberId: Long,
        @RequestParam filename: String,
        @RequestParam contentType: String,
    ): Storage.PresignedResult = service.presignUpload(memberId, filename, contentType)


    companion object {
        private const val DEFAULT_SIZE = 20

        // 상한이 없으면 size=1000000 한 방으로 방/메시지를 통째로 긁어갈 수 있다.
        private const val MAX_ROOM_SIZE = 50
        private const val MAX_MESSAGE_SIZE = 100
    }
}

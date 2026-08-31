package com.langlez.chat.api

import com.langlez.attachment.contract.Storage
import com.langlez.chat.api.request.ChatMessageSendRequest
import com.langlez.chat.api.request.ChatReportRequest
import com.langlez.chat.api.request.ChatRoomCreateRequest
import com.langlez.chat.api.response.ChatMessageResponse
import com.langlez.chat.api.response.ChatRoomResponse
import com.langlez.chat.api.response.ChatRoomSummaryResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import java.time.Instant

@Tag(name = "Chat", description = "1:1 채팅 API")
interface ChatAPI {

    @Operation(summary = "채팅방 생성/조회", description = "상대와의 방이 있으면 그대로, 없으면 새로 만들어 돌려준다.")
    fun createRoom(memberId: Long, request: ChatRoomCreateRequest): ChatRoomResponse

    @Operation(
        summary = "내 채팅방 목록",
        description = "마지막 메시지 최신순. cursor 는 직전 페이지 마지막 방의 lastMessageAt 을 넣는다. 나간 방은 빠진다.",
    )
    fun listRooms(
        memberId: Long,
        @Parameter(description = "페이지 크기(최대 50)") size: Int,
        @Parameter(description = "직전 페이지 마지막 방의 lastMessageAt") cursor: Instant?,
    ): List<ChatRoomSummaryResponse>

    @Operation(
        summary = "메시지 목록",
        description = "seq 내림차순(최신순). cursor 는 직전 페이지 마지막 메시지의 seq 를 넣는다.",
    )
    fun listMessages(
        memberId: Long,
        @Parameter(description = "방 id") roomId: Long,
        @Parameter(description = "페이지 크기(최대 100)") size: Int,
        @Parameter(description = "직전 페이지 마지막 메시지의 seq") cursor: Long?,
    ): List<ChatMessageResponse>

    @Operation(
        summary = "메시지 전송",
        description = "첨부는 upload-url 로 발급받은 key 로 확정한다. 상대가 나간 방이면 다시 되살아난다.",
    )
    fun sendMessage(
        memberId: Long,
        @Parameter(description = "방 id") roomId: Long,
        request: ChatMessageSendRequest,
    ): ChatMessageResponse

    @Operation(summary = "읽음 처리", description = "방의 메시지를 현재 시각까지 읽은 것으로 표시한다.")
    fun readRoom(memberId: Long, @Parameter(description = "방 id") roomId: Long)

    @Operation(
        summary = "채팅방 나가기",
        description = "내 목록에서만 사라진다. 상대가 다시 보내면 이전 대화 그대로 재입장한다.",
    )
    fun leaveRoom(memberId: Long, @Parameter(description = "방 id") roomId: Long)

    @Operation(summary = "메시지 삭제", description = "내가 보낸 메시지만 삭제할 수 있고 양쪽 모두에게서 지워진다.")
    fun deleteMessage(memberId: Long, @Parameter(description = "메시지 id") messageId: String)

    @Operation(summary = "상대 신고", description = "방의 상대를 신고한다. 문제가 된 메시지 id 를 함께 넘길 수 있다.")
    fun report(memberId: Long, @Parameter(description = "방 id") roomId: Long, request: ChatReportRequest)

    @Operation(
        summary = "첨부 업로드 URL 발급",
        description = "업로드용 presigned URL 과 전송에 쓸 key 를 함께 발급한다. image/·video/·audio/ 계열만 허용한다.",
    )
    fun getUploadUrl(
        memberId: Long,
        @Parameter(description = "원본 파일명") filename: String,
        @Parameter(description = "Content-Type", example = "image/jpeg") contentType: String,
    ): Storage.PresignedResult
}

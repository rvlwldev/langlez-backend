package com.langlez.chat.api.response

import com.langlez.chat.application.ChatMessageView
import com.langlez.chat.domain.ChatMessage
import io.swagger.v3.oas.annotations.media.Schema
import java.time.Instant

/** 삭제된 메시지의 본문·첨부를 가리는 건 ChatMessageView 가 이미 했다. 여기선 형태만 바꾼다. */
data class ChatMessageResponse(
    @field:Schema(description = "메시지 id") val id: String,
    @field:Schema(description = "방 안에서의 순번. 목록 조회 커서로 쓴다") val seq: Long,
    @field:Schema(description = "방 id") val roomId: Long,
    @field:Schema(description = "보낸 회원 id") val senderId: Long,
    @field:Schema(description = "메시지 종류") val type: ChatMessage.Type,
    @field:Schema(description = "본문. 삭제된 메시지는 null", nullable = true) val content: String?,
    @field:Schema(description = "첨부 조회용 URL 목록") val fileUrls: List<String>,
    @field:Schema(description = "보낸 시각") val createdAt: Instant,
    @field:Schema(description = "삭제 여부") val deleted: Boolean,
) {
    constructor(view: ChatMessageView) : this(
        id = view.id,
        seq = view.seq,
        roomId = view.roomId,
        senderId = view.senderId,
        type = view.type,
        content = view.content,
        fileUrls = view.fileUrls,
        createdAt = view.createdAt,
        deleted = view.deleted,
    )
}

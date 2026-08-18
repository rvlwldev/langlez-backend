package com.langlez.chat.api.request

import com.langlez.chat.domain.ChatMessage
import io.swagger.v3.oas.annotations.media.Schema
import jakarta.validation.constraints.Size

data class ChatMessageSendRequest(
    @field:Schema(description = "메시지 종류", example = "TEXT")
    val type: ChatMessage.Type,

    @field:Schema(description = "본문. 첨부만 보낼 땐 비워도 된다", nullable = true)
    @field:Size(max = 4000)
    val content: String? = null,

    // 첨부 확정은 key 하나당 스토리지 왕복 1회다. 상한이 없으면 요청 한 번으로 서버를 붙잡아 둘 수 있다.
    @field:Schema(description = "presign으로 발급받아 업로드 완료한 첨부 key 목록(앨범)")
    @field:Size(max = 10)
    val keys: List<String> = emptyList(),
)

package com.langlez.chat.application

/** module:attachment가 topic-chat_message를 구독해 이 모양 그대로 역직렬화한다. */
data class ChatAttachmentUploadedEvent(
    val roomId: String,
    val uploaderId: Long,
    val storageKey: String,
    val fileType: String,
)

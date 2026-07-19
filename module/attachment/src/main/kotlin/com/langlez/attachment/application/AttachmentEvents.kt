package com.langlez.attachment.application

/** module:echo가 topic-echo_post로 발행하는 이벤트 payload와 동일한 필드 모양(계약)이다. */
data class EchoAttachmentsUploadedEvent(
    val postId: String,
    val uploaderId: Long,
    val attachments: List<Item>,
) {
    data class Item(val storageKey: String, val fileType: String)
}

/** module:chat이 topic-chat_message로 발행하는 이벤트 payload와 동일한 필드 모양(계약)이다. */
data class ChatAttachmentUploadedEvent(
    val roomId: String,
    val uploaderId: Long,
    val storageKey: String,
    val fileType: String,
)

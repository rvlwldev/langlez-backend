package com.langlez.echo.application

/** module:attachment가 topic-echo_post를 구독해 이 모양 그대로 역직렬화한다. */
data class EchoAttachmentsUploadedEvent(
    val postId: String,
    val uploaderId: Long,
    val attachments: List<Item>,
) {
    data class Item(val storageKey: String, val fileType: String)
}

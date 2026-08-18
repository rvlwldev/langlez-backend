package com.langlez.core.event.chat

/**
 * 채팅 도메인 이벤트.
 *
 * 채팅은 notification/relationship 을 직접 부르지 않는다. 이 이벤트가 아웃박스를 거쳐
 * 카프카로 나가고, 받는 모듈이 각자 알림 발송·신고 저장을 한다.
 */
data class ChatMessageSentEvent(
    val roomId: Long,
    val messageId: String,
    val senderId: Long,
    val recipientId: Long,
    val preview: String,
)

data class ChatUserReportedEvent(
    val roomId: Long,
    val reporterId: Long,
    val reportedUserId: Long,
    val reason: String,
    val triggerMessageId: String?,
)

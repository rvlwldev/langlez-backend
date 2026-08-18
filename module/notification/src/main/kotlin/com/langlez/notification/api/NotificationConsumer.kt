package com.langlez.notification.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.event.chat.ChatMessageSentEvent
import com.langlez.notification.application.NotificationService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 채팅 알림 수신구.
 *
 * 컨슈머 값 역직렬화가 `StringDeserializer` 라(`application.yml`) 페이로드는 항상 문자열이다.
 * 이벤트 타입으로 바꾸는 건 여기서 한다 — 브로커 설정을 타입별 역직렬화로 바꾸면
 * 이 토픽을 읽는 다른 모듈까지 같이 묶인다.
 */
@Component
class NotificationConsumer(private val service: NotificationService, private val mapper: ObjectMapper) {

    @KafkaListener(topics = ["chat-message-sent"], groupId = "notification")
    fun onChatMessageSent(payload: String) {
        service.onChatMessage(mapper.readValue(payload, ChatMessageSentEvent::class.java))
    }
}

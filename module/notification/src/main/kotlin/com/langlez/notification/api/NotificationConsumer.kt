package com.langlez.notification.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.contract.ChatMessageSentEvent
import com.langlez.core.MessageDeduplicator
import com.langlez.notification.application.NotificationService
import com.langlez.follow.contract.MemberFollowedEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 알림 수신구.
 *
 * 컨슈머 값 역직렬화가 `StringDeserializer` 라(`application.yml`) 페이로드는 항상 문자열이다.
 * 이벤트 타입으로 바꾸는 건 여기서 한다 — 브로커 설정을 타입별 역직렬화로 바꾸면
 * 이 토픽을 읽는 다른 모듈까지 같이 묶인다.
 *
 * 중복 검사는 AOP 가 아니라 명시 호출이다. 어노테이션으로 감추면 갱신 시점이 코드에 안 보이고
 * (`@Cacheable` 을 안 쓰는 것과 같은 이유), 실패 시 표시를 되돌리는 처리가 어드바이스 안으로 숨는다.
 * 리스너가 셋뿐이라 감출 만큼 반복되지도 않는다.
 */
@Component
class NotificationConsumer(
    private val service: NotificationService,
    private val dedup: MessageDeduplicator,
    private val mapper: ObjectMapper,
) {

    @KafkaListener(topics = [CHAT_MESSAGE_SENT], groupId = "notification")
    fun onChatMessageSent(payload: String) = once(CHAT_MESSAGE_SENT, payload) {
        service.onChatMessage(mapper.readValue(payload, ChatMessageSentEvent::class.java))
    }

    @KafkaListener(topics = [MEMBER_FOLLOWED], groupId = "notification")
    fun onMemberFollowed(payload: String) = once(MEMBER_FOLLOWED, payload) {
        service.onMemberFollowed(mapper.readValue(payload, MemberFollowedEvent::class.java))
    }

    /**
     * 처음 보는 메시지일 때만 처리한다.
     *
     * 실패하면 표시를 되돌리고 예외를 그대로 올린다. 삼키면 오프셋이 커밋되어 메시지가 사라지고,
     * 되돌리지 않으면 재시도와 DLT 재투입이 전부 "중복"으로 걸러져 역시 사라진다.
     */
    private fun once(topic: String, payload: String, handle: () -> Unit) {
        if (dedup.isDuplicate(topic, payload)) return

        try {
            handle()
        } catch (e: Exception) {
            dedup.release(topic, payload)
            throw e
        }
    }

    private companion object {
        const val CHAT_MESSAGE_SENT = "chat-message-sent"
        const val MEMBER_FOLLOWED = "member-followed"
    }
}

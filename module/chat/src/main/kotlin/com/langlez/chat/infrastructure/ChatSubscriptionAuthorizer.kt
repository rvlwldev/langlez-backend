package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatRepository
import com.langlez.core.SubscriptionAuthorizer
import org.springframework.stereotype.Component

/**
 * 채팅방 토픽 구독 인가.
 *
 * 목적지를 **끝을 고정한 정규식**으로만 자기 것이라 주장한다. 심플 브로커는 구독 목적지에
 * 별표 와일드카드를 지원해서, 방 번호 자리를 느슨하게 열면 전체 방을 한 번에 빨아간다.
 * 그래서 숫자만 허용하고, 여기서 걸리지 않은 목적지는 `WebSocketSubscriptionGate` 가 거부한다.
 */
@Component
class ChatSubscriptionAuthorizer(private val repo: ChatRepository) : SubscriptionAuthorizer {

    override fun supports(destination: String) = ROOM_TOPIC_PATTERN.matches(destination)

    override fun authorize(destination: String, memberId: Long): Boolean {
        val roomId = ROOM_TOPIC_PATTERN.matchEntire(destination)?.groupValues?.get(1)?.toLongOrNull() ?: return false

        return repo.findParticipant(roomId, memberId) != null
    }

    companion object {
        private val ROOM_TOPIC_PATTERN = Regex("^/topic/chat/room/(\\d+)$")
    }
}

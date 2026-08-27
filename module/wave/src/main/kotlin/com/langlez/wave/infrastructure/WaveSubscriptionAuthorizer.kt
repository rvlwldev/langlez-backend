package com.langlez.wave.infrastructure

import com.langlez.core.SubscriptionAuthorizer
import com.langlez.wave.domain.WaveSessionRepository
import org.springframework.stereotype.Component

/**
 * 음성방 채팅 토픽 구독 인가.
 *
 * 방 번호 자리에 숫자만 허용한다. 느슨하게 열면 심플 브로커의 별표 와일드카드로
 * 전체 방을 한 번에 구독할 수 있다. 여기서 걸리지 않은 목적지는 `WebSocketSubscriptionGate` 가 거부한다.
 */
@Component
class WaveSubscriptionAuthorizer(private val sessions: WaveSessionRepository) : SubscriptionAuthorizer {

    override fun supports(destination: String) = ROOM_TOPIC_PATTERN.matches(destination)

    override fun authorize(destination: String, memberId: Long): Boolean {
        val roomId = ROOM_TOPIC_PATTERN.matchEntire(destination)?.groupValues?.get(1)?.toLongOrNull() ?: return false

        return sessions.isParticipant(roomId, memberId)
    }

    companion object {
        private val ROOM_TOPIC_PATTERN = Regex("^/topic/wave/(\\d+)/chat$")
    }
}

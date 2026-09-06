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

    override fun authorize(destination: String, memberId: Long): Boolean =
        roomIdOf(destination)?.let { sessions.isParticipant(it, memberId) } ?: false

    companion object {
        private val ROOM_TOPIC_PATTERN = Regex("^/topic/wave/(\\d+)/chat$")

        /**
         * 목적지에서 방 번호를 뽑는다. 인가와 세션 생명주기 추적이 **같은 판정**을 써야 한다 —
         * 패턴을 각자 들고 있으면 한쪽만 바뀌었을 때 구독은 되는데 끊겨도 정리가 안 되는 방이 생긴다.
         */
        internal fun roomIdOf(destination: String?): Long? =
            destination?.let { ROOM_TOPIC_PATTERN.matchEntire(it)?.groupValues?.get(1)?.toLongOrNull() }
    }
}

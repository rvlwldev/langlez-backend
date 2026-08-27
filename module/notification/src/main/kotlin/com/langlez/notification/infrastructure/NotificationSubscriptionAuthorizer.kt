package com.langlez.notification.infrastructure

import com.langlez.core.SubscriptionAuthorizer
import org.springframework.stereotype.Component

/**
 * 알림 토픽 구독 인가.
 *
 * `NotificationService` 가 `/topic/notification/{memberId}` 로 인앱 알림을 밀어주는데,
 * 이 접두사를 검사하는 인터셉터가 없어 로그인한 아무나 남의 알림(발신자 id·방 id·본문 미리보기)을
 * 실시간으로 받아갈 수 있었다. 목적지의 회원 id 와 구독자 본인이 같을 때만 허용한다.
 *
 * 저장소를 보지 않는다 — 자기 것인지 판정하는 데 필요한 정보가 목적지에 전부 있다.
 */
@Component
class NotificationSubscriptionAuthorizer : SubscriptionAuthorizer {

    override fun supports(destination: String) = TOPIC_PATTERN.matches(destination)

    override fun authorize(destination: String, memberId: Long): Boolean =
        TOPIC_PATTERN.matchEntire(destination)?.groupValues?.get(1)?.toLongOrNull() == memberId

    companion object {
        private val TOPIC_PATTERN = Regex("^/topic/notification/(\\d+)$")
    }
}

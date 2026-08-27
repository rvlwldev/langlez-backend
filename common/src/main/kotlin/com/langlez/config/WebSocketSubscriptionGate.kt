package com.langlez.config

import com.langlez.core.SubscriptionAuthorizer
import org.springframework.beans.factory.ObjectProvider
import org.springframework.messaging.Message
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.simp.SimpMessageHeaderAccessor
import org.springframework.messaging.simp.SimpMessageType
import org.springframework.messaging.simp.stomp.StompCommand
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.messaging.support.ChannelInterceptor
import org.springframework.messaging.support.MessageHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component

/**
 * SUBSCRIBE 기본 거부 게이트. 모든 구독은 여기를 한 번 지난다.
 *
 * 실시간 모듈(chat, wave)이 각자 인터셉터를 달고 "내 접두사가 아니면 통과"시키던 구조에는
 * 아무도 검사하지 않는 목적지가 남았다. `/topic` 아래 별표 두 개짜리 패턴처럼 어느 접두사에도 안 걸리는 목적지는
 * 두 인터셉터를 모두 지나 심플 브로커의 AntPathMatcher 로 모든 방을 빨아갔고,
 * `/topic/notification/{id}` 는 검사하는 인터셉터가 아예 없어 남의 알림이 그대로 열려 있었다.
 * 이건 정규식을 하나씩 덧대서 막을 문제가 아니다 — 새 모듈이 토픽을 추가할 때마다 다시 뚫린다.
 *
 * 그래서 판정 책임을 뒤집는다. 등록된 [SubscriptionAuthorizer] 중 `supports` 가 참인 것을 찾아
 * 물어보고, **하나도 없으면 거부한다.** 인가를 빠뜨린 새 토픽은 열리는 게 아니라 닫힌다.
 *
 * ### 왜 chat 이 아니라 common 인가
 * chat 이 `@EnableWebSocketMessageBroker` 를 들고 있어 인터셉터를 두기 자연스러운 자리지만,
 * 이건 채널 전체에 걸리는 보안 정책이지 채팅 도메인의 규칙이 아니다. chat 에 두면
 * "notification 토픽의 기본 거부"를 chat 모듈이 소유하게 되고, chat 을 손대는 사람이
 * 조용히 정책을 무너뜨릴 수 있다. 인증(`JwtAuthenticationFilter`)과 같은 계층에 둔다.
 *
 * ### 왜 [ObjectProvider] 인가
 * `List<SubscriptionAuthorizer>` 로 받으면 인가자가 하나도 없는 컨텍스트(실시간 모듈을 안 쓰는
 * 모듈 단위 테스트)에서 주입 자체가 실패해 기동이 깨진다. 없으면 빈 목록이어야 한다.
 */
@Component
class WebSocketSubscriptionGate(
    private val authorizers: ObjectProvider<SubscriptionAuthorizer>,
) : ChannelInterceptor {

    override fun preSend(message: Message<*>, channel: MessageChannel): Message<*> {
        val accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor::class.java)
        // STOMP accessor 가 없으면 목적지를 읽을 수 없어 판정 자체가 불가능하다. 통과시키지 않는다.
        // 현재 이 분기는 도달하지 않는다 — 인바운드 채널의 클라이언트 프레임은 StompSubProtocolHandler 가
        // 항상 StompHeaderAccessor 를 붙여 보낸다. 그래도 "못 읽으면 통과"를 남겨 두지 않는다.
        if (accessor == null) {
            if (SimpMessageHeaderAccessor.getMessageType(message.headers) == SimpMessageType.SUBSCRIBE)
                throw IllegalArgumentException(FORBIDDEN)

            return message
        }

        if (accessor.command != StompCommand.SUBSCRIBE) return message

        val destination = accessor.destination ?: throw IllegalArgumentException(FORBIDDEN)
        val authorizer = authorizers.firstOrNull { it.supports(destination) }
            ?: throw IllegalArgumentException(FORBIDDEN)

        // CONNECT 인터셉터가 심어둔 회원 id. 없으면 인증을 통과하지 않은 세션이다.
        val memberId = (accessor.user as? UsernamePasswordAuthenticationToken)?.principal as? Long
            ?: throw IllegalArgumentException("auth.unauthorized")

        if (!authorizer.authorize(destination, memberId)) throw IllegalArgumentException(FORBIDDEN)

        return message
    }

    companion object {
        private const val FORBIDDEN = "auth.forbidden"
    }
}

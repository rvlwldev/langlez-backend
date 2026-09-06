package com.langlez.wave

import com.langlez.wave.application.WaveService
import com.langlez.wave.infrastructure.WaveSubscriptionAuthorizer
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Component
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent

/**
 * 소켓이 끊긴 참여자를 방에서 뺀다.
 *
 * ### 왜 필요한가
 * 방을 닫는 경로가 명시적 퇴장과 방장 종료뿐이었다. 둘 다 클라이언트가 살아 있어야 도는 경로라
 * 앱을 강제 종료하면 아무 신호도 오지 않는다. 참여자 집합에 유령이 남아 정원을 잡아먹고,
 * 전원이 그렇게 사라진 방은 `wave_rooms.ended_at` 이 NULL 로 굳어 목록에 영구 상주한다.
 * 레디스와 달리 Postgres 행에는 만료가 없다.
 *
 * ### 왜 회원이 아니라 세션 단위인가
 * 이 세션이 듣던 방만 뺀다. 회원 단위로 빼면 같은 회원이 chat 소켓만 닫았을 때
 * 멀쩡히 통화 중인 wave 방에서까지 튕겨 나간다.
 *
 * ### 왜 인터셉터가 아니라 이벤트인가
 * `SessionSubscribeEvent` 는 인바운드 채널 전송이 성공한 뒤에만 발행된다. 그래서
 * `WebSocketSubscriptionGate` 에서 거부된 구독은 여기까지 오지 않는다.
 * 인터셉터를 추가하면 게이트보다 앞에 설 위험(configurer 순회 순서 미지정)을 다시 떠안는다.
 *
 * ### 왜 chat 의 `WebSocketSessionRegistry` 를 재사용하지 않나
 * 그건 "회원 id 로 남의 세션을 찾아 끊기"라 서버가 세션 맵을 들고 있어야 했고, 그래서
 * 등록·결속·해제 사이의 창을 일일이 막아야 했다. 여기서 필요한 건 "이 세션이 끊길 때
 * 이 세션이 듣던 방"뿐이라 세션 속성으로 끝난다 — 세션과 함께 죽으니 샐 엔트리가 없다.
 * 공유할 것이 없으므로 `common` 으로 올릴 것도, wave 가 chat 을 참조할 일도 없다.
 *
 * ### 왜 [WaveWebSocketConfiguration] 안의 `@Bean` 이 아닌가
 * 그 클래스는 `WebSocketMessageBrokerConfigurer` 라 브로커를 만들 때 먼저 필요하다.
 * 거기에 [WaveService] 를 주입하면 `SimpMessagingTemplate` → 브로커 설정 → 이 configurer →
 * `WaveService` → `MessageBroadcaster` → `SimpMessagingTemplate` 로 순환이 닫혀
 * **컨텍스트가 아예 안 뜬다.** chat 의 같은 자리(`viewingCleanupListener`)가 괜찮은 건
 * 그쪽이 브로커에서 파생되지 않는 `OnlineTracker` 만 잡기 때문이다.
 */
@Component
class WaveSessionLifecycleListener(private val service: WaveService) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /** 이 세션이 어느 방을 듣고 있는지 남긴다. 끊길 때 어디서 빼야 할지 알아야 한다. */
    @EventListener
    fun onSubscribe(event: SessionSubscribeEvent) {
        val accessor = StompHeaderAccessor.wrap(event.message)
        val roomId = WaveSubscriptionAuthorizer.roomIdOf(accessor.destination) ?: return

        accessor.sessionAttributes?.put("$LISTENING_ROOM_PREFIX$roomId", roomId)
    }

    /**
     * 방마다 따로 삼킨다. 한 방에서 던지면 그 뒤 방들이 정리되지 않은 채 남는다.
     * 그렇게 놓친 방은 [com.langlez.wave.application.WaveRoomReaper] 가 뒤늦게라도 치운다.
     */
    @EventListener
    fun onDisconnect(event: SessionDisconnectEvent) {
        val memberId = (event.user as? UsernamePasswordAuthenticationToken)?.principal as? Long ?: return
        val attributes = StompHeaderAccessor.wrap(event.message).sessionAttributes ?: return

        attributes.filterKeys { it.startsWith(LISTENING_ROOM_PREFIX) }
            .values
            .mapNotNull { it as? Long }
            .forEach { roomId ->
                runCatching { service.leave(roomId, memberId) }
                    .onFailure { logger.warn("세션 종료 후 방 퇴장 실패. room={} member={}", roomId, memberId, it) }
            }
    }

    companion object {
        private const val LISTENING_ROOM_PREFIX = "wave:listening:"
    }
}

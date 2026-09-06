package com.langlez.wave

import com.langlez.wave.application.WaveService
import com.langlez.wave.infrastructure.WaveSubscriptionAuthorizer
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationListener
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.stomp.StompHeaderAccessor
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer
import org.springframework.web.socket.messaging.SessionDisconnectEvent
import org.springframework.web.socket.messaging.SessionSubscribeEvent

/**
 * 음성방 실시간 채널.
 *
 * `@EnableWebSocketMessageBroker` 를 다시 붙이지 않는다. 그 어노테이션은 브로커 설정을 통째로
 * 가져오는 것이고, 이미 chat 모듈이 켜 뒀다. 스프링은 `WebSocketMessageBrokerConfigurer` 빈을
 * **전부 모아** 위임하므로, 여기서는 이 모듈 몫(엔드포인트)만 얹으면 된다.
 *
 * 인증(CONNECT 시점 JWT)은 chat 쪽 인터셉터가 인바운드 채널 전체에 걸어 놨고,
 * 인가(SUBSCRIBE)는 `common` 의 `WebSocketSubscriptionGate` 가 한 지점에서 처리한다.
 * 이 모듈은 `WaveSubscriptionAuthorizer` 로 "내 토픽은 이렇게 판정한다"만 선언한다 —
 * 여기서 인터셉터를 따로 달면 다시 모듈마다 기본 통과가 생긴다.
 */
@Configuration
class WaveWebSocketConfiguration(private val service: WaveService) : WebSocketMessageBrokerConfigurer {

    private val logger = LoggerFactory.getLogger(javaClass)

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // 모바일 앱 전용이라 SockJS 폴백은 두지 않는다.
        registry.addEndpoint("/ws/wave").setAllowedOriginPatterns("*")
    }

    /**
     * 이 세션이 어느 방을 듣고 있는지 세션 속성에 남긴다. 종료될 때 어디서 빼야 할지 알아야 한다.
     *
     * **인터셉터가 아니라 이벤트로 받는다.** `SessionSubscribeEvent` 는 인바운드 채널 전송이
     * 성공한 뒤에만 발행되므로, `WebSocketSubscriptionGate` 에서 거부된 구독은 여기까지 오지 않는다.
     * 인터셉터를 추가하면 게이트보다 앞에 설 위험(순서 미지정)을 다시 떠안게 된다.
     *
     * **레지스트리를 따로 두지 않는 이유**: 세션 속성은 세션과 함께 죽는다. chat 의
     * `WebSocketSessionRegistry` 는 "회원 id 로 남의 세션을 찾아 끊기" 때문에 서버가 맵을 들고
     * 있어야 했고, 그래서 엔트리가 새지 않도록 등록·결속·해제의 창을 일일이 막아야 했다.
     * 여기서 필요한 건 "이 세션이 끊길 때 이 세션이 듣던 방"뿐이라 들고 있을 맵이 없다.
     */
    @Bean
    fun waveRoomSubscribeListener(): ApplicationListener<SessionSubscribeEvent> = ApplicationListener { event ->
        val accessor = StompHeaderAccessor.wrap(event.message)
        val roomId = WaveSubscriptionAuthorizer.roomIdOf(accessor.destination) ?: return@ApplicationListener

        accessor.sessionAttributes?.put("$LISTENING_ROOM_PREFIX$roomId", roomId)
    }

    /**
     * 앱이 강제 종료되면 `DELETE .../participants/me` 가 오지 않는다. 그러면 참여자 집합에 유령이
     * 남아 정원을 잡아먹고, 전원이 그렇게 사라진 방은 `ended_at` 이 NULL 로 굳어 목록에 영구 상주한다.
     * 소켓 단절은 프레임으로 보이지 않으므로 정리는 세션 종료 이벤트에서 해야 한다.
     *
     * 이 세션이 듣던 방만 뺀다. 회원 단위로 빼면 같은 회원이 chat 소켓만 닫았을 때
     * 멀쩡히 통화 중인 wave 방에서까지 튕겨 나간다.
     *
     * 여기서 던지면 그 뒤 방들이 정리되지 않은 채 남는다. 방마다 따로 삼키고 로그로 남긴다 —
     * 놓친 방은 [com.langlez.wave.application.WaveRoomReaper] 가 뒤늦게라도 치운다.
     */
    @Bean
    fun waveRoomDisconnectListener(): ApplicationListener<SessionDisconnectEvent> = ApplicationListener { event ->
        val memberId = (event.user as? UsernamePasswordAuthenticationToken)?.principal as? Long
            ?: return@ApplicationListener

        val attributes = StompHeaderAccessor.wrap(event.message).sessionAttributes ?: return@ApplicationListener

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

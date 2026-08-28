package com.langlez.wave

import org.springframework.context.annotation.Configuration
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

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
class WaveWebSocketConfiguration : WebSocketMessageBrokerConfigurer {

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        // 모바일 앱 전용이라 SockJS 폴백은 두지 않는다.
        registry.addEndpoint("/ws/wave").setAllowedOriginPatterns("*")
    }
}

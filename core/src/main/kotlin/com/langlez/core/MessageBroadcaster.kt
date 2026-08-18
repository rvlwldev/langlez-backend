package com.langlez.core

/**
 * 실시간 메시지 전달.
 *
 * 지금 구현은 자기 인스턴스에 붙은 WebSocket 세션에만 보낸다.
 * 서버를 2대 이상으로 늘리면 이 포트의 구현만 Redis pub/sub 팬아웃으로 갈아끼우면 된다.
 * 서비스 코드가 SimpMessagingTemplate 을 직접 부르면 그때 전부 찾아 고쳐야 한다.
 */
interface MessageBroadcaster {
    fun broadcast(topic: String, payload: Any)
}

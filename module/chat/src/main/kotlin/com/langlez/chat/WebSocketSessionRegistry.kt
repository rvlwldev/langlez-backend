package com.langlez.chat

import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.socket.CloseStatus
import org.springframework.web.socket.WebSocketSession
import java.util.concurrent.ConcurrentHashMap

/**
 * 열린 WebSocket 세션을 회원별로 추적하고, 정지·탈퇴 시 끊는다.
 *
 * ### 왜 필요한가
 * CONNECT 시점 상태 검사만으로는 **새 연결만** 막힌다. 소켓은 한 번 열리면 재검증 지점이 없어
 * 이미 붙어 있던 세션이 무기한 살아남는다 — 정지된 회원이 상대 메시지를 계속 읽는다.
 * 조치를 실시간 채널까지 즉시 미치게 하려면 세션을 직접 끊는 수밖에 없다.
 *
 * ### 왜 인스턴스 전체에 전파하나
 * 정지를 처리한 인스턴스와 그 회원의 소켓이 붙어 있는 인스턴스는 다를 수 있다.
 * 조치하는 쪽은 어디에 붙어 있는지 모르므로 레디스 pub/sub 으로 전부에게 알리고 각자 자기 것만 끊는다.
 * `MessageBroadcaster` 를 재사용하지 않는 이유: 그건 STOMP 구독자에게 메시지를 미는 통로라
 * 수신 측이 `convertAndSend` 만 한다. 세션을 끊는 건 구독자에게 보낼 수 있는 일이 아니다.
 *
 * ### 왜 4계층이 아니라 모듈 루트인가
 * [ChatWebSocketConfiguration] 과 같은 이유다 — 채널 전송 계층의 장치지 채팅 도메인 규칙이 아니다.
 * `infrastructure` 에 두면 애플리케이션 이벤트 리스너(`api`)가 `infrastructure` 를 참조하게 되어
 * 의존 방향이 뒤집힌다.
 *
 * ### 남은 창
 * 세션 추적은 **이 인스턴스의 메모리**다. 인스턴스가 죽고 재기동하면 그 사이 붙은 세션은
 * 어차피 함께 끊긴다. 전파 자체가 유실되는 창(레디스 단절)은 남고, 그때는 정지된 회원의
 * 기존 세션이 살아 있다. 재연결하면 CONNECT 검사에 걸린다.
 */
@Component
class WebSocketSessionRegistry(redisson: RedissonClient) {

    private val logger = LoggerFactory.getLogger(javaClass)

    private val sessions = ConcurrentHashMap<String, WebSocketSession>()

    // 세션은 CONNECT 프레임보다 먼저 열린다. 그래서 소켓 등록과 회원 결속이 두 시점으로 갈린다.
    private val owners = ConcurrentHashMap<String, Long>()

    private val channel = redisson.getTopic(CHANNEL)
    private var listenerId: Int? = null

    @PostConstruct
    fun subscribe() {
        listenerId = channel.addListener(Signal::class.java) { _, signal -> closeLocal(signal.memberId) }
    }

    /** 해제하지 않으면 종료 중인 인스턴스가 계속 수신해 이미 끊긴 세션을 뒤진다. */
    @PreDestroy
    fun unsubscribe() {
        listenerId?.let { channel.removeListener(it) }
        listenerId = null
    }

    fun register(session: WebSocketSession) {
        sessions[session.id] = session
    }

    fun unregister(sessionId: String) {
        sessions.remove(sessionId)
        owners.remove(sessionId)
    }

    /** CONNECT 인증이 끝난 뒤 이 세션의 주인을 기록한다. 인증 전 세션은 주인이 없어 끊을 대상도 아니다. */
    fun bind(sessionId: String, memberId: Long) {
        owners[sessionId] = memberId
    }

    /** 이 회원의 열린 세션을 모든 인스턴스에서 끊는다. */
    fun terminate(memberId: Long) {
        channel.publish(Signal(memberId))
    }

    private fun closeLocal(memberId: Long) {
        val targets = owners.filterValues { it == memberId }.keys
        if (targets.isEmpty()) return

        targets.forEach { sessionId ->
            val session = sessions.remove(sessionId)
            owners.remove(sessionId)

            // 이미 끊긴 소켓을 닫으면 IOException 이 난다. 한 세션의 실패로 나머지를 남기면 안 된다.
            session?.let { runCatching { it.close(CloseStatus.POLICY_VIOLATION) } }
        }

        logger.info("실시간 세션 강제 종료. member={} sessions={}", memberId, targets.size)
    }

    /** 패키지가 `com.langlez.` 아래여야 레디스 코덱이 타입 정보를 남긴다 (RedissonConfiguration.redisCodec 참고). */
    data class Signal(val memberId: Long = 0)

    companion object {
        private const val CHANNEL = "websocket-session-terminate"
    }
}

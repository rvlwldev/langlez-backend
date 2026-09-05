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

    /**
     * CONNECT 인증이 끝난 뒤 이 세션의 주인을 기록한다. 인증 전 세션은 주인이 없어 끊을 대상도 아니다.
     *
     * **소켓은 CONNECT 프레임보다 먼저 열린다.** [register] 가 `afterConnectionEstablished` 에서
     * 항상 먼저 도므로, 여기서 세션이 안 보이면 "아직 안 열렸다"가 아니라 "이미 닫혔다"는 뜻이다.
     * 그래서 없으면 기록하지 않는 게 맞다 — 유효한 결속을 놓치는 경우가 아니다.
     *
     * **확인이 두 번인 건 중복이 아니다.** 앞의 것만 두면 검사와 기록 사이에 소켓이 닫히는 창이 남는다:
     * CONNECT 인증 중 `findStatus` 를 기다리는 사이 클라이언트가 끊기면 `afterConnectionClosed` →
     * [unregister] 가 먼저 돌아 owners 를 비우고(아직 없어서 no-op), 그 뒤 이 메서드가 항목을 남긴다.
     * 그 세션엔 다시는 [unregister] 가 불리지 않아 그 항목이 영구 잔존한다.
     * 뒤의 확인이 그 창에서 들어온 항목을 되돌린다.
     */
    fun bind(sessionId: String, memberId: Long) {
        if (!sessions.containsKey(sessionId)) return

        owners[sessionId] = memberId

        if (!sessions.containsKey(sessionId)) owners.remove(sessionId)
    }

    /** 이 회원의 열린 세션을 모든 인스턴스에서 끊는다. */
    fun terminate(memberId: Long) {
        // 로컬을 먼저 끊는다. 전파에만 의존하면 레디스가 순단일 때 정지를 처리한 바로 그 서버에
        // 붙어 있는 세션조차 안 끊긴다 — 보안 격리가 외부 인프라 장애로 무력화된다.
        // closeLocal 은 멱등이라 전파된 신호가 되돌아와도 두 번 끊지 않는다.
        closeLocal(memberId)

        // AFTER_COMMIT 리스너에서 불린다. 여기서 던지면 이미 커밋된 정지 API 가 500 을 내고,
        // 운영자가 재시도해도 member.already-suspended 로 400 이라 손 쓸 방법이 없다.
        // 다만 조용히 삼키면 보안 조치가 실패한 줄도 모른다 — 그래서 error 다.
        runCatching { channel.publish(Signal(memberId)) }
            .onFailure { logger.error("실시간 세션 강제 종료 전파 실패. member={}", memberId, it) }
    }

    /**
     * 회원당 세션 수가 아니라 이 인스턴스의 전체 세션 수에 비례해 훑는다.
     * 정지·탈퇴는 드물어 지금은 이 비용이 문제가 아니다. 잦아지면 `memberId → 세션 id` 역인덱스를 둔다.
     */
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

    /**
     * 주인이 기록된 세션 id. 생명주기 정리가 빠져 항목이 영구 잔존하는 걸 테스트가 감시한다.
     * `sessions` 는 [unregister] 가 항상 정리하지만 `owners` 는 [bind] 시점이 갈려 창이 생긴다.
     */
    internal fun trackedOwnerIds(): Set<String> = owners.keys.toSet()

    /** 패키지가 `com.langlez.` 아래여야 레디스 코덱이 타입 정보를 남긴다 (RedissonConfiguration.redisCodec 참고). */
    data class Signal(val memberId: Long = 0)

    companion object {
        private const val CHANNEL = "websocket-session-terminate"
    }
}

package com.langlez.chat.api

import com.langlez.chat.WebSocketSessionRegistry
import com.langlez.member.contract.MemberSuspendedEvent
import com.langlez.member.contract.MemberWithdrawnEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 정지·탈퇴된 회원의 실시간 세션을 끊는다.
 *
 * chat 이 이 이벤트를 받는 이유: 인바운드 채널과 STOMP 엔드포인트를 chat 이 소유한다
 * (`/ws/wave` 도 여기 얹혀 있어 wave 세션까지 함께 끊긴다). member 는 chat 을 참조하지 않고
 * 계약 이벤트만 발행한다.
 *
 * **`AFTER_COMMIT` 이다.** 아웃박스 기록(`MemberEventListener`)과 달리 이건 DB 밖의 부수 효과라
 * 원 트랜잭션이 롤백되면 일어나선 안 된다. 정지가 취소됐는데 세션만 끊기면 멀쩡한 회원이 튕긴다.
 */
@Component
class MemberStatusEventListener(private val sessions: WebSocketSessionRegistry) {

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun onMemberSuspended(event: MemberSuspendedEvent) = sessions.terminate(event.id)

    @TransactionalEventListener(phase = AFTER_COMMIT)
    fun onMemberWithdrawn(event: MemberWithdrawnEvent) = sessions.terminate(event.id)
}

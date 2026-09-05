package com.langlez.security

import com.langlez.member.contract.MemberReader
import com.langlez.member.contract.MemberReader.Status.ACTIVE
import com.langlez.member.contract.MemberReader.Status.CREATED
import com.langlez.member.contract.MemberReader.Status.SUSPENDED
import com.langlez.member.contract.MemberReader.Status.WITHDRAWN
import org.springframework.http.HttpStatus

/**
 * 계정 상태로 접근을 막을지 정하는 단일 판정표.
 *
 * **판정만 하고 예외는 던지지 않는다.** HTTP 진입점([com.langlez.filter.JwtAuthenticationFilter])은
 * `LanglezException(상태코드, 키)` 를 던지고 STOMP CONNECT 인터셉터는 `IllegalArgumentException(키)` 를
 * 던진다 — 던지는 타입이 다르니 판정과 변환을 나눠야 한 표를 공유할 수 있다.
 *
 * 이 표가 두 벌로 갈라지면 한쪽만 막히는 구멍이 난다. 실제로 그랬다 —
 * HTTP 는 매 요청 상태를 봤지만 WebSocket 은 CONNECT 에서 토큰만 보고 상태를 안 봐서,
 * 정지된 회원이 실시간 채널로 상대 메시지를 계속 읽을 수 있었다.
 */
object AccountStatusPolicy {

    /**
     * 쓸 수 있는 계정이면 `null`, 막아야 하면 사유.
     *
     * `CREATED` 는 막지 않는다. 이 상태를 `ACTIVE` 로 올리는 `Member.verify()` 호출자가 아직 0건이라
     * 실사용 회원이 전부 `CREATED` 다. 여기서 막으면 신규 가입자가 아니라 전원이 잠긴다.
     */
    fun denialOf(status: MemberReader.Status?): Denial? = when (status) {
        CREATED, ACTIVE -> null
        SUSPENDED -> Denial(HttpStatus.FORBIDDEN, "member.suspended")
        WITHDRAWN -> Denial(HttpStatus.FORBIDDEN, "member.withdrawn")
        // 회원 행은 탈퇴해도 남는다. 없다는 건 실재하지 않는 id 를 담은 토큰이라는 뜻이라
        // 갱신 경로(AuthService.refresh)와 같이 401 로 다시 로그인시킨다.
        null -> Denial(HttpStatus.UNAUTHORIZED, "auth.invalid-token")
    }

    /**
     * [status] 는 HTTP 진입점만 쓴다. STOMP 에는 상태코드 개념이 없어 [messageKey] 만 본다.
     * 그래도 한 곳에 담아 두 진입점이 같은 사유·같은 문구를 쓰게 한다.
     */
    data class Denial(val status: HttpStatus, val messageKey: String)
}

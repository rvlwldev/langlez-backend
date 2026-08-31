package com.langlez.auth.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.auth.application.AuthService
import com.langlez.core.MessageDeduplicator
import com.langlez.member.contract.MemberWithdrawnEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 탈퇴 수신구.
 *
 * 값 역직렬화기가 StringDeserializer 라 페이로드는 JSON 문자열 그대로 들어온다. 여기서 변환한다.
 *
 * 리프레시 토큰과 기기 바인딩만 지운다. 잔여 액세스 토큰을 왜 여기서 추가로 막지 않는지는
 * `AuthService.invalidateSession` 의 KDoc 참고.
 */
@Component
class AuthConsumer(
    private val service: AuthService,
    private val dedup: MessageDeduplicator,
    private val mapper: ObjectMapper,
) {

    @KafkaListener(topics = [MEMBER_WITHDRAWN], groupId = "auth")
    fun onMemberWithdrawn(payload: String) {
        if (dedup.isDuplicate(MEMBER_WITHDRAWN, payload)) return

        // 실패하면 표시를 되돌리고 예외를 올린다. 역직렬화도 이 안에 있어야 한다 —
        // 깨진 페이로드를 밖에서 풀면 표시가 남은 채 예외가 나가 그 메시지가 영영 사라진다.
        try {
            val event = mapper.readValue(payload, MemberWithdrawnEvent::class.java)
            service.invalidateSession(event.id)
        } catch (e: Exception) {
            dedup.release(MEMBER_WITHDRAWN, payload)
            throw e
        }
    }

    private companion object {
        const val MEMBER_WITHDRAWN = "member-withdrawn"
    }
}

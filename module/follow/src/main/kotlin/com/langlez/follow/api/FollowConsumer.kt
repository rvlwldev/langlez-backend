package com.langlez.follow.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.block.contract.MemberBlockedEvent
import com.langlez.core.MessageDeduplicator
import com.langlez.follow.application.FollowService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 차단 수신 → 팔로우 양방향 해제.
 *
 * 차단과 팔로우가 다른 모듈 소유라 한 트랜잭션에 묶을 수 없다. block 이 `member-blocked` 를
 * 발행하고 여기서 받아 끊는다. 그 사이 팔로우 행이 남는 창이 열리지만
 * **읽기 경로가 전부 `BlockReader` 로 먼저 걸러서 사용자에게는 아무것도 보이지 않는다** —
 * 자세한 근거는 `BlockService.block` KDoc 에 있다.
 *
 * 값 역직렬화기가 StringDeserializer 라 페이로드는 JSON 문자열 그대로 들어온다. 여기서 변환한다.
 *
 * 해제 자체가 멱등(없는 관계를 지워도 성공)이라 재배달로 여러 번 돌아도 안전하다.
 * 그래도 `MessageDeduplicator` 를 앞에 두는 건 불필요한 DELETE 왕복을 줄이기 위해서다.
 */
@Component
class FollowConsumer(
    private val service: FollowService,
    private val dedup: MessageDeduplicator,
    private val mapper: ObjectMapper,
) {

    @KafkaListener(topics = [MEMBER_BLOCKED], groupId = "follow")
    fun onMemberBlocked(payload: String) {
        if (dedup.isDuplicate(MEMBER_BLOCKED, payload)) return

        // 실패하면 표시를 되돌리고 예외를 올린다. 되돌리지 않으면 재시도와 DLT 재투입이
        // 전부 "중복"으로 걸러져 그 차단의 팔로우 해제가 영영 일어나지 않는다.
        // 역직렬화도 이 안에 있어야 한다 — 깨진 페이로드를 밖에서 풀면 표시가 남은 채 예외가 나가
        // 바로 그 유실이 일어난다.
        try {
            val event = mapper.readValue(payload, MemberBlockedEvent::class.java)

            service.unfollowBothWays(event.blockerId, event.blockedId)
        } catch (e: Exception) {
            dedup.release(MEMBER_BLOCKED, payload)
            throw e
        }
    }

    private companion object {
        const val MEMBER_BLOCKED = "member-blocked"
    }
}

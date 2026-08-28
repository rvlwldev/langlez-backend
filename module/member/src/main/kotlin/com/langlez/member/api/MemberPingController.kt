package com.langlez.member.api

import com.langlez.annotation.MemberId
import com.langlez.core.OnlineTracker
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 앱 생존 신호(핑) 전용 엔드포인트.
 *
 * 30초 간격 하트비트라 Kafka 를 거치면 접속자 수만큼 브로커 왕복 + 컨슈머가 매초 붙는다.
 * 접속 판정에 필요한 건 Redis 버킷 쓰기 하나뿐이라 여기서 직행한다.
 * 회원 식별도 토큰의 id 를 그대로 쓰므로 handle→id 조회가 사라진다.
 * TTL(1분) 안에 다음 핑이 오지 않으면 버킷이 만료되며 자동으로 오프라인이 된다.
 */
@RestController
@RequestMapping("/api/v1/members")
class MemberPingController(private val tracker: OnlineTracker) {

    @PostMapping("/me/ping")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun ping(@MemberId memberId: Long) = tracker.toOnline(memberId)
}

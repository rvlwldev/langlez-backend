package com.langlez.profile.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.contract.MemberCreatedEvent
import com.langlez.profile.application.ProfileService
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 프로필 수신구.
 *
 * 컨슈머 값 역직렬화가 `StringDeserializer` 라(`application.yml`) 페이로드는 항상 문자열이다.
 * 이벤트 타입으로 바꾸는 건 여기서 한다.
 *
 * `MessageDeduplicator` 를 쓰지 않는다. 그쪽은 처리 성공 전에 표시를 남겨 프로세스가 강제 종료되면
 * 그 메시지를 영영 걸러 버린다 — 프로필이 없으면 프로필 API 가 통째로 404 라 유실 비용이 크다.
 * 여기서는 "이미 있으면 아무것도 안 한다"가 곧 멱등이라 존재 확인만으로 충분하고, 재배달을 몇 번 받아도 안전하다.
 */
@Component
class ProfileConsumer(
    private val service: ProfileService,
    private val mapper: ObjectMapper,
) {

    @KafkaListener(topics = [MEMBER_CREATED], groupId = "profile")
    fun onMemberCreated(payload: String) {
        service.createProfileIfAbsent(mapper.readValue(payload, MemberCreatedEvent::class.java).id)
    }

    private companion object {
        const val MEMBER_CREATED = "member-created"
    }
}

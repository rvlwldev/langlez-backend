package com.langlez.relationship.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.event.chat.ChatUserReportedEvent
import com.langlez.relationship.application.RelationshipService
import com.langlez.relationship.domain.Report
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 채팅 신고 수신.
 *
 * 값 역직렬화기가 StringDeserializer 라 페이로드는 JSON 문자열 그대로 들어온다. 여기서 변환한다.
 *
 * 저장 자체는 `RelationshipService.report` 가 멱등이다 — 카프카는 at-least-once 라
 * 리밸런싱·재시도로 같은 이벤트가 다시 온다. 컨슈머에서 따로 중복 상태를 들고 있지 않는다
 * (인스턴스가 여러 대면 로컬 중복 집합은 어차피 못 막는다).
 *
 * 방 id 를 `sourceId` 로 남긴다. 채팅 신고는 "이 방에서 상대가 이랬다" 가 단위라 그래야 운영이 추적한다.
 */
@Component
class RelationshipConsumer(private val service: RelationshipService, private val mapper: ObjectMapper) {

    @KafkaListener(topics = ["chat-user-reported"], groupId = "relationship")
    fun onChatUserReported(payload: String) {
        val event = mapper.readValue(payload, ChatUserReportedEvent::class.java)

        service.report(
            reporterId = event.reporterId,
            reportedUserId = event.reportedUserId,
            sourceType = Report.SourceType.CHAT_USER,
            sourceId = event.roomId.toString(),
            reason = event.reason,
            triggerMessageId = event.triggerMessageId,
        )
    }
}

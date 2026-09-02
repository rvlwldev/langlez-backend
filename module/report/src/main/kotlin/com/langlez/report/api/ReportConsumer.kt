package com.langlez.report.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.contract.ChatUserReportedEvent
import com.langlez.core.MessageDeduplicator
import com.langlez.report.application.ReportService
import com.langlez.report.domain.Report
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 채팅 신고 수신.
 *
 * 값 역직렬화기가 StringDeserializer 라 페이로드는 JSON 문자열 그대로 들어온다. 여기서 변환한다.
 *
 * 중복 배달 방어가 두 겹이다. `MessageDeduplicator` 가 같은 레코드의 재배달을 앞에서 걷어내고,
 * 그걸 통과해도 `ReportService.report` 의 존재 검사가 같은 신고를 두 행으로 만들지 않는다.
 * 레디스 장애 시 중복 검사는 통과(fail-open)로 흐르므로 뒤쪽 방어선이 계속 필요하다.
 *
 * 방 id 를 `sourceId` 로 남긴다. 채팅 신고는 "이 방에서 상대가 이랬다" 가 단위라 그래야 운영이 추적한다.
 */
@Component
class ReportConsumer(
    private val service: ReportService,
    private val dedup: MessageDeduplicator,
    private val mapper: ObjectMapper,
) {

    @KafkaListener(topics = [CHAT_USER_REPORTED], groupId = "report")
    fun onChatUserReported(payload: String) {
        if (dedup.isDuplicate(CHAT_USER_REPORTED, payload)) return

        // 실패하면 표시를 되돌리고 예외를 올린다. 되돌리지 않으면 재시도와 DLT 재투입이
        // 전부 "중복"으로 걸러져 신고가 통째로 사라진다.
        // 역직렬화도 이 안에 있어야 한다 — 깨진 페이로드를 밖에서 풀면 표시가 남은 채 예외가 나가
        // 바로 그 유실이 일어난다.
        try {
            val event = mapper.readValue(payload, ChatUserReportedEvent::class.java)

            service.report(
                reporterId = event.reporterId,
                reportedUserId = event.reportedUserId,
                sourceType = Report.SourceType.CHAT_USER,
                sourceId = event.roomId.toString(),
                reason = event.reason,
                triggerMessageId = event.triggerMessageId,
            )
        } catch (e: Exception) {
            dedup.release(CHAT_USER_REPORTED, payload)
            throw e
        }
    }

    private companion object {
        const val CHAT_USER_REPORTED = "chat-user-reported"
    }
}

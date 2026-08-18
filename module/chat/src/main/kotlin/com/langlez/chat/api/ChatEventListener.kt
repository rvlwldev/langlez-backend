package com.langlez.chat.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.infrastructure.jpa.ChatOutBoxRepository
import com.langlez.chat.infrastructure.outbox.ChatOutBox
import com.langlez.core.event.chat.ChatUserReportedEvent
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 신고 이벤트를 아웃박스 행으로 남긴다.
 *
 * BEFORE_COMMIT 이라 신고 처리와 아웃박스 기록이 한 트랜잭션에 묶인다.
 * 커밋 뒤에 남기면 그 사이 장애로 이벤트만 통째로 사라진다.
 * 키를 방 id 로 두면 같은 방의 이벤트가 같은 파티션에 들어가 순서가 보장된다.
 *
 * 메시지 전송은 여기 없다 — 저빈도인 신고와 달리 가장 빈번한 쓰기라 아웃박스 행을 하나 더 쓰면
 * 비용이 두 배가 된다. Mongo 문서의 `published` 플래그와 `ChatMessagePublisher` 가 그 역할을 대신한다.
 */
@Component
class ChatEventListener(private val repo: ChatOutBoxRepository, private val mapper: ObjectMapper) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onUserReported(event: ChatUserReportedEvent) {
        repo.save(ChatOutBox("CHAT", "chat-user-reported", mapper.writeValueAsString(event), event.roomId.toString()))
    }
}

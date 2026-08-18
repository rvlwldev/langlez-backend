package com.langlez.chat.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.core.OnlineTracker
import com.langlez.core.event.chat.ChatMessageSentEvent
import com.langlez.redis.distributedLock.DistributedLock
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit.SECONDS

/**
 * 미발행 메시지를 알림 이벤트로 내보낸다.
 *
 * 아웃박스 테이블이 없다 — 메시지 문서의 `published` 플래그가 그 역할을 한다. 아웃박스를 따로 두면
 * 이 앱에서 가장 빈번한 쓰기(메시지 전송)마다 행이 하나 더 생기는데, Mongo 는 단일 문서 쓰기가
 * 원자적이라 플래그만으로 같은 보장을 얻는다.
 *
 * **발행 여부를 전송 시점이 아니라 여기서 정한다.** 전송 때 정하면 그 사이 상대가 방에 들어오거나
 * 나가는 변화를 놓친다. 발행 직전이 가장 최신 상태다.
 */
@Component
internal class ChatMessagePublisher(
    private val messages: ChatMessageRepository,
    private val repo: ChatRepository,
    private val tracker: OnlineTracker,
    private val kafka: KafkaTemplate<String, String>,
    private val mapper: ObjectMapper,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedDelay = 1000)
    @DistributedLock(prefix = "lock:chat-message-publish", throwOnFailure = false)
    fun publish() = messages.findUnpublished(CHUNK).forEach { message ->
        // 성공한 것만 표시한다. 실패하면 published 가 false 로 남아 다음 주기에 다시 잡힌다 — 이게 아웃박스 대용의 핵심이다.
        runCatching { send(message) }
            .onSuccess { messages.save(message.apply { markPublished() }) }
            .onFailure { logger.warn("채팅 알림 발행 실패, 다음 주기에 재시도한다: messageId={}", message.id, it) }
    }

    /**
     * 수신자가 그 방을 보고 있으면 아무것도 보내지 않는다. 메시지는 이미 WebSocket 으로 화면에 떴고,
     * 그 위에 푸시나 인앱 알림을 겹치면 같은 내용을 두 번 보게 된다.
     * 앱을 아예 안 켠 경우(푸시)와 다른 화면을 보는 경우(인앱)의 구분은 이벤트를 받는 notification 이 한다.
     */
    private fun send(message: ChatMessage) {
        // 1:1 방이라 발신자가 아닌 참여자가 곧 수신자다. 없으면 보낼 곳이 없으니 발행 표시만 하고 끝낸다.
        val recipient = repo.findParticipants(message.roomId).firstOrNull { it.memberId != message.senderId } ?: return
        if (recipient.memberId in tracker.viewers(topic(message.roomId))) return

        val event = ChatMessageSentEvent(
            roomId = message.roomId,
            messageId = requireNotNull(message.id),
            senderId = message.senderId,
            recipientId = recipient.memberId,
            // 사진·음성은 목록에 보여줄 본문이 없다. 타입만 남기고 문구는 클라이언트가 현지화한다.
            preview = message.preview(),
        )

        // 키가 방 id 라 같은 방의 알림이 한 파티션에 모여 순서가 보장된다.
        // 결과를 기다려야 실패를 안다. 안 기다리면 브로커가 죽어도 published 로 표시돼 알림이 통째로 사라진다.
        kafka.send(TOPIC, message.roomId.toString(), mapper.writeValueAsString(event)).get(TIMEOUT_SECS, SECONDS)
    }

    private fun topic(roomId: Long) = "/topic/chat/room/$roomId"

    private companion object {
        const val CHUNK = 500
        const val TOPIC = "chat-message-sent"
        const val TIMEOUT_SECS = 5L
    }
}

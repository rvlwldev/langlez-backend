package com.langlez.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.MessageBroadcaster
import com.langlez.notification.contract.Notificator
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.contract.PushTokenQuery
import com.langlez.chat.contract.ChatMessageSentEvent
import com.langlez.relationship.contract.MemberFollowedEvent
import com.langlez.exception.LanglezException
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.domain.PushSender
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * 알림 발송과 이력.
 *
 * `core.Notificator` 를 이 클래스가 직접 구현한다. 다른 모듈이 부르는 창구(notify)와
 * 상태 판정·이력 저장이 같은 흐름이라, 위임만 하는 어댑터를 하나 더 두면 파일만 늘어난다.
 */
@Service
class NotificationService(
    private val repo: NotificationRepository,
    private val tracker: OnlineTracker,
    private val broadcaster: MessageBroadcaster,
    private val tokens: PushTokenQuery,
    private val push: PushSender,
    private val mapper: ObjectMapper,
) : Notificator {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 수신자 하나짜리 호출도 다건 경로 하나로 태운다. 두 벌을 두면 한쪽만 고치는 사고가 난다.
    override fun notify(memberId: Long, type: String, title: String, body: String, data: Map<String, String>) =
        notifyAll(listOf(memberId), type, title, body, data)

    /**
     * 이력을 먼저 남기고 전달한다. 전달이 실패해도 목록에는 남아야 한다.
     *
     * 트랜잭션을 걸지 않았다 — `repo.saveAll` 이 자기 트랜잭션을 갖고, 뒤따르는 브로드캐스트와
     * FCM 은 네트워크 I/O 라 DB 커넥션을 쥔 채 외부를 기다리면 풀이 마른다.
     */
    override fun notifyAll(
        memberIds: Collection<Long>,
        type: String,
        title: String,
        body: String,
        data: Map<String, String>,
    ) {
        val recipients = memberIds.toSet()
        if (recipients.isEmpty()) return

        val payload = data.takeIf { it.isNotEmpty() }?.let(mapper::writeValueAsString)
        val saved = repo.saveAll(
            recipients.map { memberId ->
                Notification(recipientId = memberId, type = type, title = title, body = body, data = payload)
            }
        )

        // 포그라운드에서는 OS 가 FCM 배너를 안 그리므로 인앱과 푸시를 항상 같이 보내도 중복 노출이 없다.
        // 수신자별 토픽이라 N 번 발행할 수밖에 없다 — Redis pub/sub 이라 싸다.
        saved.forEach {
            broadcaster.broadcast("$NOTIFICATION_TOPIC_PREFIX${it.recipientId}", NotificationView(it, data))
        }

        // 토큰이 없으면(로그아웃·푸시 거부) 보낼 곳이 없다. 이력은 이미 남았으니 조용히 끝낸다.
        val tokensByMember = tokens.findPushTokens(recipients)
        if (tokensByMember.isEmpty()) return

        // 전송 실패로 컨슈머를 실패시키지 않는다. 죽은 토큰은 재시도해도 같은 결과인데,
        // 그동안 파티션이 막혀 뒤에 쌓인 다른 사람 알림까지 늦어진다.
        val failed = runCatching { push.sendAll(tokensByMember.values, title, body, data) }
            .onFailure { logger.warn("FCM 다건 푸시 실패, 알림 이력만 남는다: recipients={}", recipients.size, it) }
            .getOrDefault(emptyList())

        if (failed.isNotEmpty()) {
            logger.warn("FCM 푸시 일부 실패: {}건 중 {}건", tokensByMember.size, failed.size)
        }
    }

    /**
     * 그 방을 보고 있으면 아무것도 보내지 않는다. 메시지는 이미 화면에 떴다.
     * chat 의 발행 폴러가 한 번 거르지만, 발행과 소비 사이에 방에 들어온 사람은 걸러지지 않아 여기서 다시 본다.
     */
    fun onChatMessage(event: ChatMessageSentEvent) {
        if (event.recipientId in tracker.viewers("$CHAT_ROOM_TOPIC_PREFIX${event.roomId}")) return

        notify(
            memberId = event.recipientId,
            type = TYPE_CHAT_MESSAGE,
            // 발신자 표시명이 이벤트에 없고, 본문 미리보기도 "[IMAGE]" 처럼 클라이언트가 현지화하는 값이다.
            // 제목도 같은 방식으로 메시지 키를 넘겨 클라이언트가 사용자 언어로 그린다.
            title = TITLE_CHAT_MESSAGE,
            body = event.preview,
            data = mapOf(
                "roomId" to event.roomId.toString(),
                "messageId" to event.messageId,
                "senderId" to event.senderId.toString(),
            ),
        )
    }

    /**
     * 팔로우 알림.
     *
     * 자기 자신 팔로우는 `Follow` 엔티티 생성 시점에 막히므로 여기서 다시 보지 않는다.
     * 중복 방어를 흩뿌리면 어느 쪽이 진짜 방어선인지 아무도 모르게 된다.
     */
    fun onMemberFollowed(event: MemberFollowedEvent) = notify(
        memberId = event.followedId,
        type = TYPE_MEMBER_FOLLOWED,
        // 채팅과 같은 규약이다. 발신자 표시명을 서버가 조회하지 않고 메시지 키만 넘겨
        // 클라이언트가 사용자 언어로 그린다.
        title = TITLE_MEMBER_FOLLOWED,
        // 채팅의 preview 에 해당하는 동적 본문이 없다. 표시 문구는 클라이언트가
        // followerId 로 프로필을 붙여 조립하므로 서버가 채울 값 자체가 없고,
        // 정적 문장을 키로 하나 더 두면 클라이언트가 안 쓰는 번역이 12개 늘 뿐이다.
        // Notification.body 가 nullable = false 라 빈 문자열을 넣는다.
        body = "",
        data = mapOf("followerId" to event.followerId.toString()),
    )

    fun list(memberId: Long, size: Int, cursor: Long?): List<Notification> = repo.findAll(memberId, size, cursor)

    fun markRead(memberId: Long, id: Long) {
        val notification = repo.find(id) ?: throw LanglezException(NOT_FOUND, "notification.not-found")
        // id 만 알면 남의 알림도 읽음 처리할 수 있다. 소유자를 반드시 확인한다.
        if (notification.recipientId != memberId) throw LanglezException(FORBIDDEN, "notification.forbidden")

        repo.save(notification.apply { read = true })
    }

    companion object {
        const val TYPE_CHAT_MESSAGE = "CHAT_MESSAGE"
        const val TITLE_CHAT_MESSAGE = "notification.chat-message.title"

        const val TYPE_MEMBER_FOLLOWED = "MEMBER_FOLLOWED"
        const val TITLE_MEMBER_FOLLOWED = "notification.member-followed"

        private const val NOTIFICATION_TOPIC_PREFIX = "/topic/notification/"
        private const val CHAT_ROOM_TOPIC_PREFIX = "/topic/chat/room/"
    }
}

/** 인앱 알림으로 밀어주는 실시간 페이로드. */
data class NotificationView(
    val id: Long,
    val type: String,
    val title: String,
    val body: String,
    val data: Map<String, String>,
    val createdAt: Instant,
) {
    constructor(notification: Notification, data: Map<String, String>) : this(
        id = notification.id,
        type = notification.type,
        title = notification.title,
        body = notification.body,
        data = data,
        createdAt = notification.createdAt,
    )
}

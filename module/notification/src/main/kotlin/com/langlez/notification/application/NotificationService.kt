package com.langlez.notification.application

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.MessageBroadcaster
import com.langlez.notification.contract.Notificator
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.contract.PushTokenReader
import com.langlez.chat.contract.ChatMessageSentEvent
import com.langlez.relationship.contract.MemberFollowedEvent
import com.langlez.exception.LanglezException
import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationMuteRepository
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.domain.NotificationSetting
import com.langlez.notification.domain.NotificationSettingRepository
import com.langlez.notification.domain.PushSender
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

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
    private val tokens: PushTokenReader,
    private val push: PushSender,
    private val mapper: ObjectMapper,
    private val mutes: NotificationMuteRepository,
    private val settingsRepo: NotificationSettingRepository,
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
     *
     * 인앱과 푸시를 다르게 취급한다 — 이 구분이 핵심이다.
     * - 유형 mute 는 그 알림 자체를 없던 일로 한다: 이력도 안 남고 푸시도 안 간다.
     * - 방해금지 시간대는 푸시만 막는다: 이력·인앱 브로드캐스트는 그대로 남긴다.
     *   방해금지가 이력까지 지우면 아침에 열었을 때 밤새 온 알림이 통째로 사라진다 —
     *   그건 "설정"이 아니라 "유실"이다.
     *
     * 설정(mute·quiet) 조회가 실패해도 발송 자체를 죽이지 않는다. 바로 아래 FCM 호출이
     * 이미 같은 판단(runCatching + warn)을 하고 있다 — 설정을 못 읽으면 전부 보내는 쪽(기존 동작)으로
     * 흐르게 하고 warn 만 남긴다.
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

        val mutedBy = runCatching { mutes.findAll(recipients) }
            .onFailure { logger.warn("알림 mute 설정 조회 실패, 전부 발송한다: recipients={}", recipients.size, it) }
            .getOrDefault(emptyMap())
        val active = recipients.filterNot { type in mutedBy[it].orEmpty() }.toSet()
        if (active.isEmpty()) return

        val payload = data.takeIf { it.isNotEmpty() }?.let(mapper::writeValueAsString)
        val saved = repo.saveAll(
            active.map { memberId ->
                Notification(recipientId = memberId, type = type, title = title, body = body, data = payload)
            }
        )

        // 포그라운드에서는 OS 가 FCM 배너를 안 그리므로 인앱과 푸시를 항상 같이 보내도 중복 노출이 없다.
        // 수신자별 토픽이라 N 번 발행할 수밖에 없다 — Redis pub/sub 이라 싸다.
        saved.forEach {
            broadcaster.broadcast("$NOTIFICATION_TOPIC_PREFIX${it.recipientId}", NotificationView(it, data))
        }

        val quietBy = runCatching { settingsRepo.findAll(active) }
            .onFailure { logger.warn("방해금지 설정 조회 실패, 푸시는 전부 보낸다: recipients={}", active.size, it) }
            .getOrDefault(emptyList())
            .associateBy(NotificationSetting::memberId)
        val now = Instant.now()
        val pushTargets = active.filterNot { quietBy[it]?.isQuietAt(now) == true }.toSet()
        if (pushTargets.isEmpty()) return

        // 토큰이 없으면(로그아웃·푸시 거부) 보낼 곳이 없다. 이력은 이미 남았으니 조용히 끝낸다.
        val tokensByMember = tokens.findPushTokens(pushTargets)
        if (tokensByMember.isEmpty()) return

        // 전송 실패로 컨슈머를 실패시키지 않는다. 죽은 토큰은 재시도해도 같은 결과인데,
        // 그동안 파티션이 막혀 뒤에 쌓인 다른 사람 알림까지 늦어진다.
        val failed = runCatching { push.sendAll(tokensByMember.values, title, body, data) }
            .onFailure { logger.warn("FCM 다건 푸시 실패, 알림 이력만 남는다: recipients={}", pushTargets.size, it) }
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

    @Transactional(readOnly = true)
    fun settingsOf(memberId: Long): NotificationSettingSnapshot {
        val setting = settingsRepo.find(memberId)
        return NotificationSettingSnapshot(
            mutedTypes = mutes.find(memberId),
            quietFrom = setting?.quietFrom,
            quietTo = setting?.quietTo,
            timeZone = setting?.timeZone,
        )
    }

    /**
     * `types` 는 전체 교체다. 개별 추가/삭제 엔드포인트를 두지 않아 검사가 한 곳에 모인다.
     *
     * 방해금지(`updateQuietHours`)와 엔드포인트·메서드를 분리했다 — 한 리소스로 묶어 PATCH 로
     * 받으면, 클라이언트가 mute 목록만 보낼 때 방해금지 필드가 DTO 기본값(`null`/빈 값)으로
     * 조용히 덮어써진다. PUT 두 개로 쪼개면 각각이 자기 리소스의 전체 교체라 그 모호함이 없다.
     */
    @Transactional
    fun updateMutes(memberId: Long, types: Set<String>): Set<String> {
        val unknown = types - VALID_TYPES
        if (unknown.isNotEmpty()) throw LanglezException(BAD_REQUEST, "notification.type.unknown")

        mutes.replaceAll(memberId, types)
        return types
    }

    /** `from`/`to` 는 둘 다 있거나 둘 다 없어야 한다(`NotificationSetting.updateQuietHours`). */
    @Transactional
    fun updateQuietHours(memberId: Long, from: LocalTime?, to: LocalTime?, timeZone: String?): NotificationSetting {
        if (timeZone != null) {
            runCatching { ZoneId.of(timeZone) }
                .getOrElse { throw LanglezException(BAD_REQUEST, "notification.time-zone.invalid", it) }
        }

        val setting = settingsRepo.find(memberId) ?: NotificationSetting(memberId = memberId)
        try {
            setting.updateQuietHours(from, to, timeZone)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }
        return settingsRepo.save(setting)
    }

    companion object {
        const val TYPE_CHAT_MESSAGE = "CHAT_MESSAGE"
        const val TITLE_CHAT_MESSAGE = "notification.chat-message.title"

        const val TYPE_MEMBER_FOLLOWED = "MEMBER_FOLLOWED"
        const val TITLE_MEMBER_FOLLOWED = "notification.member-followed"

        /** [com.langlez.notification.domain.NotificationMute.type] 로 저장 가능한 값 전체. */
        private val VALID_TYPES = setOf(TYPE_CHAT_MESSAGE, TYPE_MEMBER_FOLLOWED)

        private const val NOTIFICATION_TOPIC_PREFIX = "/topic/notification/"
        private const val CHAT_ROOM_TOPIC_PREFIX = "/topic/chat/room/"
    }
}

/** 알림 수신 설정 조회/수정 결과. `NotificationSetting`+`NotificationMute` 두 테이블을 묶은 값이라 별도 엔티티가 아니다. */
data class NotificationSettingSnapshot(
    val mutedTypes: Set<String>,
    val quietFrom: LocalTime?,
    val quietTo: LocalTime?,
    val timeZone: String?,
)

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

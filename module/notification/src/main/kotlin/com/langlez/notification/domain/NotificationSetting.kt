package com.langlez.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 회원당 1행, 방해금지 시간대 전용.
 *
 * `member_id` 는 `members(id)` 를 가리키지만 물리적 FK 는 걸지 않는다 — 다른 모듈 테이블을
 * JPA 로 참조하지 않는 규약을 따른다(`Profile` 의 `member_profiles.id` 참고).
 */
@Entity
@Table(name = "notification_settings")
class NotificationSetting(
    @Id
    @Column(name = "member_id")
    val memberId: Long,

    @Column(name = "quiet_from")
    var quietFrom: LocalTime? = null,

    @Column(name = "quiet_to")
    var quietTo: LocalTime? = null,

    @Column(name = "time_zone", length = 64)
    var timeZone: String? = null,
) {
    /**
     * `from`/`to` 는 둘 다 있거나 둘 다 없어야 한다. 같은 시각(`from == to`)은 "24시간 내내"가
     * 아니라 400 으로 거절한다 — 반열린 구간 `[from, to)` 규칙에서 `from == to` 는 `from <= to`
     * 분기를 타 [isQuietAt] 이 항상 false(미적용)로 조용히 뒤집힌다. 하루 종일 막고 싶으면
     * 방해금지가 아니라 유형 mute 를 쓰는 게 맞다.
     */
    fun updateQuietHours(from: LocalTime?, to: LocalTime?, zone: String?) {
        require((from == null) == (to == null)) { "notification.quiet-hours.incomplete" }
        require(from == null || from != to) { "notification.quiet-hours.invalid-range" }

        quietFrom = from
        quietTo = to
        timeZone = zone
    }

    /**
     * 방해금지 판정. `time_zone` 이 없거나 파싱에 실패하면 무조건 false(미적용) 다.
     * 서버 시간으로 대신 판정하면 해외 사용자가 자기 낮 시간에 조용히 막힐 수 있어,
     * 판정이 안 될 때는 "막지 않는다" 쪽으로 fail-open 한다.
     */
    fun isQuietAt(now: Instant): Boolean {
        val from = quietFrom ?: return false
        val to = quietTo ?: return false
        val zone = timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: return false

        val local = now.atZone(zone).toLocalTime()
        // 자정을 넘는 구간(예: 22:00~07:00)은 단순 부등식 비교로는 절대 참이 안 된다.
        return if (from <= to) local >= from && local < to else local >= from || local < to
    }
}

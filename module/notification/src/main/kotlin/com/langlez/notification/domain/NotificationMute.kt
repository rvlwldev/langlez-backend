package com.langlez.notification.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable

/**
 * 끈 유형만 행으로 남는다. 행이 없으면 켜진 상태(기본 on) 다.
 *
 * `type` 은 enum 이 아니라 문자열이다. 현재 [com.langlez.notification.application.NotificationService] 가
 * 쓰는 값: `CHAT_MESSAGE`, `MEMBER_FOLLOWED`.
 */
@Entity
@IdClass(NotificationMute.Key::class)
@Table(name = "notification_mutes")
class NotificationMute(
    @Id
    @Column(name = "member_id")
    val memberId: Long,

    @Id
    @Column(length = 50)
    val type: String,
) {
    data class Key(val memberId: Long = 0, val type: String = "") : Serializable
}

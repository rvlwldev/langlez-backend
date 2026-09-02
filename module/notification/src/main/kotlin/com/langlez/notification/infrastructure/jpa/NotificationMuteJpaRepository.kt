package com.langlez.notification.infrastructure.jpa

import com.langlez.notification.domain.NotificationMute
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationMuteJpaRepository : JpaRepository<NotificationMute, NotificationMute.Key> {
    fun findByMemberId(memberId: Long): List<NotificationMute>
    fun findByMemberIdIn(memberIds: Collection<Long>): List<NotificationMute>
    fun deleteByMemberId(memberId: Long)
}

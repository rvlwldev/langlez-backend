package com.langlez.notification.infrastructure

import com.langlez.notification.domain.NotificationMute
import com.langlez.notification.domain.NotificationMuteRepository
import com.langlez.notification.infrastructure.jpa.NotificationMuteJpaRepository
import org.springframework.stereotype.Repository

@Repository
class NotificationMuteRepositoryImpl(
    private val jpa: NotificationMuteJpaRepository,
) : NotificationMuteRepository {

    override fun find(memberId: Long): Set<String> = jpa.findByMemberId(memberId).map { it.type }.toSet()

    override fun findAll(memberIds: Collection<Long>): Map<Long, Set<String>> {
        if (memberIds.isEmpty()) return emptyMap()
        return jpa.findByMemberIdIn(memberIds.toSet())
            .groupBy({ it.memberId }, { it.type })
            .mapValues { it.value.toSet() }
    }

    // 트랜잭션 경계는 호출부(application)가 정한다 — 설정 저장과 한 트랜잭션으로 묶여야 한다.
    override fun replaceAll(memberId: Long, types: Set<String>) {
        jpa.deleteByMemberId(memberId)
        if (types.isNotEmpty()) jpa.saveAll(types.map { NotificationMute(memberId = memberId, type = it) })
    }
}

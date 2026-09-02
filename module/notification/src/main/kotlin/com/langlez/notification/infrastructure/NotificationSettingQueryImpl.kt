package com.langlez.notification.infrastructure

import com.langlez.notification.contract.NotificationSettingQuery
import com.langlez.notification.domain.NotificationMuteRepository
import com.langlez.notification.domain.NotificationSettingRepository
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * `notification` 이 다른 모듈에 내주는 설정 조회 포트 구현.
 *
 * `NotificationRepositoryImpl` 과 합치지 않는다 — 그건 `Notification`(알림 이력) 엔티티의
 * CRUD 어댑터라 성격이 다르다. `MemberQueryImpl` 처럼 합칠 기존 후보가 없어 새로 둔다.
 */
@Repository
class NotificationSettingQueryImpl(
    private val mutes: NotificationMuteRepository,
    private val settingsRepo: NotificationSettingRepository,
) : NotificationSettingQuery {

    override fun mutedTypesOf(memberId: Long): Set<String> = mutes.find(memberId)

    override fun mutedTypesOf(memberIds: Collection<Long>): Map<Long, Set<String>> = mutes.findAll(memberIds)

    override fun isQuietNow(memberId: Long): Boolean = settingsRepo.find(memberId)?.isQuietAt(Instant.now()) ?: false
}

package com.langlez.notification.infrastructure

import com.langlez.notification.domain.NotificationSetting
import com.langlez.notification.domain.NotificationSettingRepository
import com.langlez.notification.infrastructure.jpa.NotificationSettingJpaRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class NotificationSettingRepositoryImpl(
    private val jpa: NotificationSettingJpaRepository,
) : NotificationSettingRepository {

    override fun find(memberId: Long): NotificationSetting? = jpa.findByIdOrNull(memberId)

    override fun findAll(memberIds: Collection<Long>): List<NotificationSetting> {
        if (memberIds.isEmpty()) return emptyList()
        return jpa.findAllById(memberIds.toSet())
    }

    override fun save(setting: NotificationSetting): NotificationSetting = jpa.save(setting)
}

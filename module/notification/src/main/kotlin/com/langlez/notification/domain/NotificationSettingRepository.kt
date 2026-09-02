package com.langlez.notification.domain

interface NotificationSettingRepository {
    fun find(memberId: Long): NotificationSetting?

    /** 행이 없는 회원은 결과에서 빠진다(방해금지 미설정). */
    fun findAll(memberIds: Collection<Long>): List<NotificationSetting>

    fun save(setting: NotificationSetting): NotificationSetting
}

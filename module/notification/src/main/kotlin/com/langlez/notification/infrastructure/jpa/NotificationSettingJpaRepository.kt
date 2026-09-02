package com.langlez.notification.infrastructure.jpa

import com.langlez.notification.domain.NotificationSetting
import org.springframework.data.jpa.repository.JpaRepository

interface NotificationSettingJpaRepository : JpaRepository<NotificationSetting, Long>

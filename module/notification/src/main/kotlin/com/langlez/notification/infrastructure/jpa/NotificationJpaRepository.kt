package com.langlez.notification.infrastructure.jpa

import com.langlez.notification.domain.Notification
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface NotificationJpaRepository : JpaRepository<Notification, Long>

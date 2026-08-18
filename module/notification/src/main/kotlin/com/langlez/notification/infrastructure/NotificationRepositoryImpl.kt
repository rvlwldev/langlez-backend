package com.langlez.notification.infrastructure

import com.langlez.notification.domain.Notification
import com.langlez.notification.domain.NotificationRepository
import com.langlez.notification.infrastructure.jpa.NotificationJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import com.langlez.notification.domain.QNotification.Companion.notification as QNotification

/**
 * 캐시를 두지 않는다. 읽음 표시로 계속 바뀌는 데다, 목록은 커서마다 달라 적중률이 사실상 없다.
 */
@Repository
class NotificationRepositoryImpl(
    private val jpa: NotificationJpaRepository,
    private val dsl: JPAQueryFactory,
) : NotificationRepository {

    override fun save(notification: Notification): Notification = jpa.save(notification)

    override fun find(id: Long): Notification? = jpa.findByIdOrNull(id)

    /** 정렬·커서를 created_at 이 아니라 id 로 잡는다. 서버 시계가 어긋나면 같은 시각이 겹쳐 페이지가 새거나 겹친다. */
    override fun findAll(recipientId: Long, size: Int, cursor: Long?): List<Notification> =
        dsl.selectFrom(QNotification)
            .where(QNotification.recipientId.eq(recipientId), cursor?.let { QNotification.id.lt(it) })
            .orderBy(QNotification.id.desc())
            .limit(size.toLong())
            .fetch()
}

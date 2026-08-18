package com.langlez.chat.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "chat_room_members",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_CHAT_ROOM_MEMBER", columnNames = ["room_id", "member_id"])],
    indexes = [Index(name = "IDX_CHAT_ROOM_MEMBER_MEMBER", columnList = "member_id")]
)
class ChatRoomMember(
    @Column(name = "room_id", nullable = false) val roomId: Long,
    @Column(name = "member_id", nullable = false) val memberId: Long,
    @Column(name = "last_read_at") var lastReadAt: Instant? = null,
    @Column(name = "left_at") var leftAt: Instant? = null,

    /**
     * 안 읽은 수를 참여자 행에 비정규화해 둔다.
     * 메시지가 Mongo 로 가면서 Postgres 조인으로는 셀 수 없게 됐고, 방마다 Mongo 집계를 돌리면
     * 목록 50개당 왕복 50번이 붙는다. 카운터를 여기 두면 방 목록이 여전히 쿼리 1회로 끝난다.
     *
     * 증가는 `ChatRepository.increaseUnread` 가 DB 에서 직접 더한다. 여기서 읽고-쓰기로 올리면
     * 같은 상대에게 연속 전송할 때 증가가 유실된다.
     */
    @Column(name = "unread_count", nullable = false) var unreadCount: Long = 0,
) {
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    // 읽음 시각은 되돌리지 않는다. 늦게 도착한 오래된 읽음 처리가 앞선 값을 덮으면 안 읽은 수가 되살아난다.
    // 카운터도 같은 이유로 시각이 실제로 전진할 때만 비운다.
    fun markRead(at: Instant) {
        if (lastReadAt != null && at <= lastReadAt) return

        lastReadAt = at
        unreadCount = 0
    }

    /**
     * 다시 센 값으로 카운터를 맞춘다(대사 전용).
     *
     * 반드시 "더하기"가 아니라 "설정"이어야 한다. 대사가 놓친 만큼 더하는 방식이면
     * 두 번 돌 때마다 같은 메시지를 또 더해 배지가 부풀어 오른다.
     */
    fun syncUnread(count: Long) {
        unreadCount = count
    }

    fun leave(at: Instant = Instant.now()) {
        leftAt = at
    }

    /** 재입장 정책: 나가도 이전 대화는 그대로 보인다. leftAt 만 해제한다. */
    fun rejoin() {
        leftAt = null
    }

    fun hasLeft(): Boolean = leftAt != null
}

package com.langlez.chat.infrastructure.jpa

import com.langlez.chat.domain.ChatRoomMember
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMember, Long> {

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomMember?

    fun findAllByRoomId(roomId: Long): List<ChatRoomMember>

    /** 읽고-쓰기가 아니라 DB 에서 더한다. 동시 전송 시 증가 유실을 막는 유일한 방법이다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatRoomMember m set m.unreadCount = m.unreadCount + 1 where m.roomId = :roomId and m.memberId = :memberId")
    fun increaseUnread(roomId: Long, memberId: Long)

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatRoomMember m set m.leftAt = null where m.roomId = :roomId and m.memberId = :memberId")
    fun rejoin(roomId: Long, memberId: Long)

    /**
     * 읽음 처리.
     * 읽고-쓰기가 아니라 조건부(lastReadAt is null or lastReadAt < :at) DB 단일 UPDATE 로 처리하여,
     * 읽는 시점과 저장 시점 사이에 도착한 새 메시지의 unreadCount 증가분 유실을 방지한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        update ChatRoomMember m
        set m.unreadCount = 0, m.lastReadAt = :at
        where m.roomId = :roomId and m.memberId = :memberId and (m.lastReadAt is null or m.lastReadAt < :at)
    """)
    fun markRead(roomId: Long, memberId: Long, at: Instant): Int
}

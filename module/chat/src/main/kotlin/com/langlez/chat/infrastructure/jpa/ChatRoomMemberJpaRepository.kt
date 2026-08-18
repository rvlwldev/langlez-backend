package com.langlez.chat.infrastructure.jpa

import com.langlez.chat.domain.ChatRoomMember
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMember, Long> {

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomMember?

    fun findAllByRoomId(roomId: Long): List<ChatRoomMember>

    /** 읽고-쓰기가 아니라 DB 에서 더한다. 동시 전송 시 증가 유실을 막는 유일한 방법이다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update ChatRoomMember m set m.unreadCount = m.unreadCount + 1 where m.roomId = :roomId and m.memberId = :memberId")
    fun increaseUnread(roomId: Long, memberId: Long)
}

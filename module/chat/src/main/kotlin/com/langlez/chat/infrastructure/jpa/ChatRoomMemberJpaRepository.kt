package com.langlez.chat.infrastructure.jpa

import com.langlez.chat.domain.ChatRoomMember
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomMemberJpaRepository : JpaRepository<ChatRoomMember, Long> {

    fun findByRoomIdAndMemberId(roomId: Long, memberId: Long): ChatRoomMember?

    fun findAllByRoomId(roomId: Long): List<ChatRoomMember>
}

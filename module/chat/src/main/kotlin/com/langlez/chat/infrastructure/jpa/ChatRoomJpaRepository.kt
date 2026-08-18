package com.langlez.chat.infrastructure.jpa

import com.langlez.chat.domain.ChatRoom
import org.springframework.data.jpa.repository.JpaRepository

interface ChatRoomJpaRepository : JpaRepository<ChatRoom, Long>

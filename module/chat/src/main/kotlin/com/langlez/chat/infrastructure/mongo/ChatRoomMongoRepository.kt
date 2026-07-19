package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatRoom
import org.springframework.data.mongodb.repository.MongoRepository

interface ChatRoomMongoRepository : MongoRepository<ChatRoom, String> {
    fun findByParticipantIds(participantIds: List<Long>): ChatRoom?
}

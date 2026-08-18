package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.mongodb.repository.MongoRepository
import java.time.Instant

interface ChatMessageMongoRepository : MongoRepository<ChatMessage, String> {

    fun findAllByRoomIdOrderBySeqDesc(roomId: Long, pageable: Pageable): List<ChatMessage>

    fun findAllByRoomIdAndSeqLessThanOrderBySeqDesc(roomId: Long, seq: Long, pageable: Pageable): List<ChatMessage>

    fun findAllByPublishedFalse(pageable: Pageable): List<ChatMessage>


    fun findFirstByRoomIdOrderBySeqDesc(roomId: Long): ChatMessage?

    /** 읽은 시각을 seq 경계로 환산한다. 그 시각까지 화면에 떠 있던 마지막 메시지가 곧 경계다. */
    fun findFirstByRoomIdAndCreatedAtLessThanEqualOrderBySeqDesc(roomId: Long, at: Instant): ChatMessage?

    fun countByRoomIdAndSeqGreaterThanAndSenderIdNot(roomId: Long, seq: Long, senderId: Long): Long
}

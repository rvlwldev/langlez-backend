package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.stereotype.Repository
import java.time.Instant

interface ChatMessageMongoRepository : MongoRepository<ChatMessage, String>

@Repository
class ChatMessageRepositoryImpl(
    private val mongoRepo: ChatMessageMongoRepository,
    private val mongoTemplate: MongoTemplate,
) : ChatMessageRepository {

    override fun save(message: ChatMessage): ChatMessage {
        return mongoRepo.save(message)
    }

    override fun findByRoom(roomId: String, cursor: String?, size: Int): List<ChatMessage> {
        val query = Query()
        query.addCriteria(Criteria.where("roomId").`is`(roomId))

        if (cursor != null) {
            val cursorMsg = mongoTemplate.findById(cursor, ChatMessage::class.java)
            if (cursorMsg != null) {
                query.addCriteria(
                    Criteria().orOperator(
                        Criteria.where("createdAt").lt(cursorMsg.createdAt),
                        Criteria().andOperator(
                            Criteria.where("createdAt").`is`(cursorMsg.createdAt),
                            Criteria.where("id").lt(cursor)
                        )
                    )
                )
            }
        }

        query.with(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        query.limit(size)
        return mongoTemplate.find(query, ChatMessage::class.java)
    }

    override fun countUnread(roomId: String, memberId: Long, after: Instant): Long {
        val query = Query()
        query.addCriteria(
            Criteria.where("roomId").`is`(roomId)
                .and("senderId").ne(memberId)
                .and("createdAt").gt(after)
        )
        return mongoTemplate.count(query, ChatMessage::class.java)
    }

    override fun findAttachments(cursor: String?, size: Int): List<ChatMessage> {
        val query = Query()
        query.addCriteria(Criteria.where("type").ne(ChatMessage.Type.TEXT))

        if (cursor != null) {
            val cursorMsg = mongoTemplate.findById(cursor, ChatMessage::class.java)
            if (cursorMsg != null) {
                query.addCriteria(
                    Criteria().orOperator(
                        Criteria.where("createdAt").lt(cursorMsg.createdAt),
                        Criteria().andOperator(
                            Criteria.where("createdAt").`is`(cursorMsg.createdAt),
                            Criteria.where("id").lt(cursor)
                        )
                    )
                )
            }
        }

        query.with(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        query.limit(size)
        return mongoTemplate.find(query, ChatMessage::class.java)
    }

    override fun findByRoomSince(roomId: String, since: Instant): List<ChatMessage> {
        val query = Query()
        query.addCriteria(
            Criteria.where("roomId").`is`(roomId)
                .and("createdAt").gt(since)
        )
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"))
        return mongoTemplate.find(query, ChatMessage::class.java)
    }
}

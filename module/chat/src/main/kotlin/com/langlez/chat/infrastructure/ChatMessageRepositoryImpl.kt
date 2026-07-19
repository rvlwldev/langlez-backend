package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.infrastructure.mongo.ChatMessageMongoRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.stereotype.Repository
import java.time.Instant

import org.springframework.data.mongodb.core.query.Update

@Repository
class ChatMessageRepositoryImpl(
    private val mongoRepo: ChatMessageMongoRepository,
    private val mongoTemplate: MongoTemplate,
) : ChatMessageRepository {

    override fun save(message: ChatMessage): ChatMessage {
        return mongoRepo.save(message)
    }

    override fun findById(id: String): ChatMessage? {
        return mongoRepo.findById(id).orElse(null)
    }

    override fun findByIds(ids: List<String>): List<ChatMessage> {
        if (ids.isEmpty()) return emptyList()
        val query = Query(Criteria.where("id").`in`(ids))
        return mongoTemplate.find(query, ChatMessage::class.java)
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

    override fun findByRoomSince(roomId: String, since: Instant): List<ChatMessage> {
        val query = Query()
        query.addCriteria(
            Criteria.where("roomId").`is`(roomId)
                .and("createdAt").gt(since)
        )
        query.with(Sort.by(Sort.Direction.ASC, "createdAt"))
        return mongoTemplate.find(query, ChatMessage::class.java)
    }

    override fun markDeleted(messageId: String, deletedAt: Instant) {
        val query = Query(Criteria.where("id").`is`(messageId))
        val update = Update().set("deletedAt", deletedAt)
        mongoTemplate.updateFirst(query, update, ChatMessage::class.java)
    }

    override fun findLastMessage(roomId: String): ChatMessage? {
        val query = Query(Criteria.where("roomId").`is`(roomId))
            .with(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
            .limit(1)
        return mongoTemplate.findOne(query, ChatMessage::class.java)
    }
}

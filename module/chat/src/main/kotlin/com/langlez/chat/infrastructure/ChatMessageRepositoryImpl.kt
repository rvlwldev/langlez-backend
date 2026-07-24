package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.infrastructure.mongo.ChatMessageMongoRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.aggregation.Aggregation
import org.springframework.data.mongodb.core.mapping.Field
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

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
            var cursorTime: Instant? = null
            var cursorId: String = cursor
            var parsed = false

            val parts = cursor.split("_", limit = 2)
            if (parts.size == 2) {
                val epoch = parts[0].toLongOrNull()
                if (epoch != null) {
                    cursorTime = Instant.ofEpochMilli(epoch)
                    cursorId = parts[1]
                    parsed = true
                }
            }

            if (!parsed) {
                val cursorMsg = mongoTemplate.findById(cursor, ChatMessage::class.java)
                if (cursorMsg != null) {
                    cursorTime = cursorMsg.createdAt
                    cursorId = cursorMsg.id ?: cursor
                    parsed = true
                }
            }

            if (parsed && cursorTime != null) {
                query.addCriteria(
                    Criteria().orOperator(
                        Criteria.where("createdAt").lt(cursorTime),
                        Criteria().andOperator(
                            Criteria.where("createdAt").`is`(cursorTime),
                            Criteria.where("id").lt(cursorId)
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

    override fun countUnreadBatch(rooms: List<ChatRoom>, memberId: Long): Map<String, Long> {
        if (rooms.isEmpty()) return emptyMap()

        val criteriaList = rooms.mapNotNull { room ->
            val roomId = room.id ?: return@mapNotNull null
            val lastReadAt = room.readStatus[memberId] ?: Instant.EPOCH
            Criteria.where("roomId").`is`(roomId).and("createdAt").gt(lastReadAt)
        }

        if (criteriaList.isEmpty()) return emptyMap()

        val matchCriteria = Criteria.where("senderId").ne(memberId)
            .andOperator(Criteria().orOperator(*criteriaList.toTypedArray()))

        val aggregation = Aggregation.newAggregation(
            Aggregation.match(matchCriteria),
            Aggregation.group("roomId").count().`as`("count")
        )

        val results = mongoTemplate.aggregate(
            aggregation,
            ChatMessage::class.java,
            UnreadCountResult::class.java
        )

        val resultMap = results.mappedResults.associate { it.roomId to it.count }
        return rooms.associate { room -> (room.id ?: "") to (resultMap[room.id] ?: 0L) }
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

private data class UnreadCountResult(
    @Field("_id")
    val roomId: String,
    val count: Long
)

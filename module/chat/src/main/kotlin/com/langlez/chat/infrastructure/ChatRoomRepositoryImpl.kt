package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.chat.infrastructure.mongo.ChatRoomMongoRepository
import org.springframework.data.domain.Sort
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.mongodb.core.query.Update
import org.springframework.stereotype.Repository
import java.time.Instant

@Repository
class ChatRoomRepositoryImpl(
    private val mongoRepo: ChatRoomMongoRepository,
    private val mongoTemplate: MongoTemplate,
) : ChatRoomRepository {

    override fun findByParticipants(memberId1: Long, memberId2: Long): ChatRoom? {
        val sortedIds = listOf(memberId1, memberId2).sorted()
        return mongoRepo.findByParticipantIds(sortedIds)
    }

    override fun save(room: ChatRoom): ChatRoom {
        return mongoRepo.save(room)
    }

    override fun findByParticipant(memberId: Long, cursor: String?, size: Int): List<ChatRoom> {
        val query = Query()
        query.addCriteria(Criteria.where("participantIds").`in`(memberId))

        if (cursor != null) {
            val cursorRoom = mongoTemplate.findById(cursor, ChatRoom::class.java)
            if (cursorRoom != null) {
                val cursorTime = cursorRoom.lastMessageAt
                if (cursorTime != null) {
                    query.addCriteria(
                        Criteria().orOperator(
                            Criteria.where("lastMessageAt").lt(cursorTime),
                            Criteria().andOperator(
                                Criteria.where("lastMessageAt").`is`(cursorTime),
                                Criteria.where("id").lt(cursor)
                            ),
                            Criteria.where("lastMessageAt").`is`(null)
                        )
                    )
                } else {
                    query.addCriteria(
                        Criteria().andOperator(
                            Criteria.where("lastMessageAt").`is`(null),
                            Criteria.where("id").lt(cursor)
                        )
                    )
                }
            }
        }

        query.with(Sort.by(Sort.Direction.DESC, "lastMessageAt", "id"))
        query.limit(size)
        return mongoTemplate.find(query, ChatRoom::class.java)
    }

    override fun findAllRooms(cursor: String?, size: Int): List<ChatRoom> {
        val query = Query()

        if (cursor != null) {
            val cursorRoom = mongoTemplate.findById(cursor, ChatRoom::class.java)
            if (cursorRoom != null) {
                val cursorTime = cursorRoom.createdAt
                query.addCriteria(
                    Criteria().orOperator(
                        Criteria.where("createdAt").lt(cursorTime),
                        Criteria().andOperator(
                            Criteria.where("createdAt").`is`(cursorTime),
                            Criteria.where("id").lt(cursor)
                        )
                    )
                )
            }
        }

        query.with(Sort.by(Sort.Direction.DESC, "createdAt", "id"))
        query.limit(size)
        return mongoTemplate.find(query, ChatRoom::class.java)
    }

    override fun updateReadStatus(roomId: String, memberId: Long, readAt: Instant) {
        val query = Query(Criteria.where("id").`is`(roomId))
        val update = Update().set("readStatus.$memberId", readAt)
        mongoTemplate.updateFirst(query, update, ChatRoom::class.java)
    }

    override fun findById(roomId: String): ChatRoom? {
        return mongoRepo.findById(roomId).orElse(null)
    }
}

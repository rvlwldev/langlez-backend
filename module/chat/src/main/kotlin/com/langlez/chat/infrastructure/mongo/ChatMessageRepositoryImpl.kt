package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import org.redisson.api.RedissonClient
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant

/**
 * 메시지 저장소 어댑터.
 *
 * 조건이 전부 단일·이중 필드라 파생 쿼리로 충분하다. 캐시는 두지 않는다 — 방마다 계속 새 메시지가 붙는다.
 */
@Repository
class ChatMessageRepositoryImpl(
    private val mongo: ChatMessageMongoRepository,
    // 파생 쿼리로는 특정 필드만 중복 없이 뽑을 수 없다. 대사 후보 조회에만 쓴다.
    private val template: MongoTemplate,
    private val redisson: RedissonClient,
) : ChatMessageRepository {

    /**
     * 방별 번호표. 레디스 INCR 한 번이라 전송 경로에 왕복이 하나만 붙는다.
     * (Mongo `findAndModify` 로 세면 가장 빈번한 경로에 쓰기가 한 번 더 생긴다.)
     */
    override fun nextSeq(roomId: Long): Long {
        val counter = redisson.getAtomicLong(seqKey(roomId))
        val next = counter.incrementAndGet()

        // 레디스가 키를 잃으면(플러시·강제 축출) 번호가 1 로 되돌아가 새 메시지가 옛 메시지 아래로 정렬된다.
        // 1 이 나온 순간에만 Mongo 의 최대 seq 로 다시 맞춘다. 방마다 최초 1회뿐이라 비용이 없다.
        if (next != 1L) return next

        val max = mongo.findFirstByRoomIdOrderBySeqDesc(roomId)?.seq ?: return 1L
        return (max + 1).also { counter.compareAndSet(1L, it) }
    }

    override fun save(message: ChatMessage): ChatMessage = mongo.save(message)

    override fun find(id: String): ChatMessage? = mongo.findByIdOrNull(id)

    override fun findByRoom(roomId: Long, size: Int, cursor: Long?): List<ChatMessage> {
        val page = PageRequest.ofSize(size)

        return cursor
            ?.let { mongo.findAllByRoomIdAndSeqLessThanOrderBySeqDesc(roomId, it, page) }
            ?: mongo.findAllByRoomIdOrderBySeqDesc(roomId, page)
    }

    override fun findUnpublished(limit: Int): List<ChatMessage> =
        mongo.findAllByPublishedFalse(PageRequest.ofSize(limit))


    /**
     * 방 id 만 뽑는다. 문서를 통째로 들고 와서 중복을 걸러 내면 최근 창의 메시지 전부가 힙에 올라온다.
     *
     * ponytail: 활성 방 수만큼 뒤에서 왕복이 붙는다. 방이 많아지면 창을 좁히거나(1분)
     * 집계 파이프라인으로 방별 마지막 seq 까지 한 번에 가져오는 쪽으로 올린다.
     */
    override fun findRoomIdsSince(since: Instant): List<Long> = template.findDistinct(
        Query(Criteria.where("createdAt").gte(since)),
        "roomId",
        ChatMessage::class.java,
        Long::class.javaObjectType,
    )

    /**
     * 시각을 seq 경계로 한 번 환산한 뒤 그 뒤를 센다.
     *
     * 목록·커서가 seq 기준이라 개수도 같은 기준이어야 한다. createdAt 으로 바로 세면 시계가 어긋난
     * 인스턴스의 메시지가 목록 맨 위에는 뜨는데 배지에는 안 잡히는(또는 그 반대) 상태가 된다.
     * 환산 자체는 시각 비교일 수밖에 없지만, 그건 한 번뿐이고 그 뒤로는 순서가 흔들리지 않는다.
     */
    override fun countUnread(roomId: Long, memberId: Long, lastReadAt: Instant?): Long {
        val readSeq = lastReadAt
            ?.let { mongo.findFirstByRoomIdAndCreatedAtLessThanEqualOrderBySeqDesc(roomId, it)?.seq }
            ?: 0L

        return mongo.countByRoomIdAndSeqGreaterThanAndSenderIdNot(roomId, readSeq, memberId)
    }

    private fun seqKey(roomId: Long) = "chat:seq:$roomId"
}

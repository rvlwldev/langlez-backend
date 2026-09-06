package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import org.redisson.api.RAtomicLong
import org.redisson.api.RedissonClient
import org.springframework.data.domain.PageRequest
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.data.mongodb.core.query.Query
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.concurrent.TimeUnit

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
     *
     * 카운터가 없을 때(레디스가 키를 잃었거나 최초 전송)만 Mongo 의 최대 seq 로 되맞춘다.
     * 예전에는 `incrementAndGet` 으로 먼저 1 을 뽑은 뒤 그 결과를 보고 되맞췄는데, 그 왕복 사이에
     * 다른 스레드가 이미 2, 3 을 들고 나가면 `compareAndSet(1L, max+1)` 이 실패해 카운터가
     * 낮은 값에 영구히 고정됐다(B-02). 그래서 지금은 **되맞추는 동안 아무도 증가시키지 못하게**
     * 초기화 자체를 락으로 직렬화하고, 초기화가 끝난 뒤에만 모두가 `incrementAndGet` 을 부른다.
     * 카운터가 이미 있으면(대부분의 호출) 락을 안 타 핫 패스 비용이 그대로다.
     */
    override fun nextSeq(roomId: Long): Long {
        val counter = redisson.getAtomicLong(seqKey(roomId))
        if (!counter.isExists) initSeq(roomId, counter)
        return counter.incrementAndGet()
    }

    /** 방 하나당 최초 1회(또는 카운터 유실 뒤 1회)만 타는 콜드 패스. */
    private fun initSeq(roomId: Long, counter: RAtomicLong) {
        val lock = redisson.getLock(seqInitLockKey(roomId))
        lock.lock(10, TimeUnit.SECONDS)
        try {
            // 락을 기다리는 동안 다른 스레드가 이미 초기화를 끝냈을 수 있다.
            if (!counter.isExists) counter.set(mongo.findFirstByRoomIdOrderBySeqDesc(roomId)?.seq ?: 0L)
        } finally {
            if (lock.isHeldByCurrentThread) lock.unlock()
        }
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

    private fun seqInitLockKey(roomId: Long) = "lock:chat-seq-init:$roomId"
}

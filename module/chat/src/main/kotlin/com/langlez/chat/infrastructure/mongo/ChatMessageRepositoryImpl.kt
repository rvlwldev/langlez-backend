package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.SeqLockTimeoutException
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
     * 방별 번호표. `isExists` 확인 후 `incrementAndGet` 이라 정상 경로도 레디스 왕복이 2회(EXISTS + INCR)다.
     * (예전엔 `INCR` 한 번뿐이었다. Lua 로 한 번에 묶으면 1회로 되돌릴 수 있지만 이번 스코프는 아니다.)
     *
     * 카운터가 없을 때(레디스가 키를 잃었거나 최초 전송)만 Mongo 의 최대 seq 로 되맞춘다.
     * 예전에는 `incrementAndGet` 으로 먼저 1 을 뽑은 뒤 그 결과를 보고 되맞췄는데, 그 왕복 사이에
     * 다른 스레드가 이미 2, 3 을 들고 나가면 `compareAndSet(1L, max+1)` 이 실패해 카운터가
     * 낮은 값에 영구히 고정됐다(B-02). 그래서 지금은 **되맞추는 동안 아무도 증가시키지 못하게**
     * 초기화 자체를 락으로 직렬화하고, 초기화가 끝난 뒤에만 모두가 `incrementAndGet` 을 부른다.
     * 카운터가 이미 있어도(대부분의 호출) `isExists` 확인은 여전히 거친다 — 락은 안 타지만 왕복은 준다.
     */
    override fun nextSeq(roomId: Long): Long {
        val counter = redisson.getAtomicLong(seqKey(roomId))
        if (!counter.isExists) initSeq(roomId, counter)
        return counter.incrementAndGet()
    }

    /**
     * 방 하나당 최초 1회(또는 카운터 유실 뒤 1회)만 타는 콜드 패스.
     *
     * `tryLock(waitTime, unit)` 을 쓴다 — 인자 하나짜리 `lock(leaseTime, unit)` 은 waitTime 이 아니라
     * **락 점유 유지 시간**이라 무제한 대기 + watchdog 비활성화라는 정반대의 동작이 된다(B-02 수정 리뷰).
     * `tryLock(waitTime, unit)` 오버로드는 watchdog(기본 TTL 30초, 10초마다 자동 갱신)이 그대로 살아 있어
     * `initSeq` 가 오래 걸려도(Mongo 지연·페일오버) 락이 조기 만료돼 상호 배제가 깨지는 일이 없다.
     * 대신 대기 자체는 10초로 막아, 락을 쥔 스레드가 죽었거나 Mongo 가 완전히 응답을 멈춘 최악의
     * 경우에도 요청 스레드가 무한정 잠기지 않고 실패로 끝난다.
     */
    private fun initSeq(roomId: Long, counter: RAtomicLong) {
        val lock = redisson.getLock(seqInitLockKey(roomId))
        if (!lock.tryLock(10, TimeUnit.SECONDS)) throw SeqLockTimeoutException("chat.seq.lock-timeout")

        try {
            // 락을 기다리는 동안 다른 스레드가 이미 초기화를 끝냈을 수 있다.
            if (!counter.isExists) counter.set(mongo.findFirstByRoomIdOrderBySeqDesc(roomId)?.seq ?: 0L)
        } finally {
            // watchdog 이 살아 있는 채로 락이 이미 만료됐을 수 있다 — 그때 unlock() 을 부르면
            // IllegalMonitorStateException 이 난다.
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

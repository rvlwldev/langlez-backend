package com.langlez.chat.infrastructure

import com.langlez.chat.domain.ChatRepository
import com.langlez.chat.domain.ChatRoom
import com.langlez.chat.domain.ChatRoomMember
import com.langlez.chat.domain.ChatRoomSummary
import com.langlez.chat.domain.QChatRoomMember
import com.langlez.chat.infrastructure.jpa.ChatRoomJpaRepository
import com.langlez.chat.infrastructure.jpa.ChatRoomMemberJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Propagation.REQUIRES_NEW
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.langlez.chat.domain.QChatRoom.Companion.chatRoom as QChatRoom

/**
 * 방·참여자 저장소 어댑터.
 *
 * 캐시를 두지 않는다. 방은 계속 바뀌고 목록 정렬 키(lastMessageAt)가 메시지마다 갱신돼
 * 캐시가 맞을 틈이 거의 없다.
 */
@Repository
class ChatRepositoryImpl(
    private val rooms: ChatRoomJpaRepository,
    private val participants: ChatRoomMemberJpaRepository,
    private val dsl: JPAQueryFactory,
) : ChatRepository {

    /**
     * 1:1 방은 정규화된 회원 쌍(member_a < member_b)으로 식별한다.
     *
     * 참여자 테이블을 두 번 조인하는 방식은 인자 순서와 무관하다는 장점이 있었지만
     * "쌍"에 유니크를 걸 자리가 없어 동시 생성 시 방이 둘로 갈렸다(V19 가 UNQ_CHAT_ROOM_PAIR 를 건다).
     * 조회도 같은 컬럼으로 옮긴다 — 앱이 찾는 조건과 DB 가 막는 조건이 어긋나면
     * 앱이 "없다"고 본 방을 DB 가 거부해 500 이 된다. 정규화 규칙은 [ChatRoom.between] 과 같다.
     */
    override fun findRoomBetween(a: Long, b: Long): ChatRoom? =
        dsl.selectFrom(QChatRoom)
            .where(QChatRoom.memberA.eq(minOf(a, b)), QChatRoom.memberB.eq(maxOf(a, b)))
            .fetchFirst()

    /**
     * 방과 참여자 두 행은 한 트랜잭션이어야 한다. 쪼개지면 참여자 없는 방이 남는다.
     *
     * 회원 쌍 충돌(동시 생성)은 여기서 삼키지 않는다. 이 트랜잭션은 제약 위반 시점에 이미
     * rollback-only 로 표시돼 잡아 봐야 커밋에서 `UnexpectedRollbackException` 이 난다.
     * 이 트랜잭션이 끝난 뒤 `ChatService.getOrCreateRoom` 이 밖에서 잡아 기존 방을 돌려준다.
     *
     * **`REQUIRES_NEW` 를 지우지 마라.** 지금은 컨트롤러에서만 들어와 외부 트랜잭션이 없으니
     * 기본 전파(`REQUIRED`)로도 같게 동작한다. 하지만 매칭 성사 처리·온보딩·배치처럼
     * 이미 `@Transactional` 인 곳에서 `getOrCreateRoom` 을 부르는 순간, 이 메서드가 그 트랜잭션에
     * 그대로 참여해 유니크 위반이 **외부 트랜잭션을 rollback-only 로 마킹한다.** 그러면 서비스가
     * 예외를 잡아 기존 방을 정상 반환해도 외부 커밋에서 `UnexpectedRollbackException` 이 나
     * 500 이 된다 — 밖에서 잡는다는 설계 자체가 호출 컨텍스트에 따라 무너진다.
     *
     * 대가로 커넥션을 하나 더 쓴다(바깥 트랜잭션이 자기 커넥션을 쥔 채 이쪽을 기다린다).
     * 방 생성은 사람이 채팅을 처음 여는 순간에만 일어나는 저빈도 경로라 그 비용을 감수한다.
     */
    @Transactional(propagation = REQUIRES_NEW)
    override fun createRoom(a: Long, b: Long): ChatRoom = rooms.save(ChatRoom.between(a, b)).also { room ->
        participants.saveAll(listOf(ChatRoomMember(room.id, a), ChatRoomMember(room.id, b)))
    }

    override fun findRoom(roomId: Long): ChatRoom? = rooms.findByIdOrNull(roomId)

    override fun findParticipant(roomId: Long, memberId: Long): ChatRoomMember? =
        participants.findByRoomIdAndMemberId(roomId, memberId)

    override fun findParticipants(roomId: Long): List<ChatRoomMember> = participants.findAllByRoomId(roomId)

    override fun saveParticipant(p: ChatRoomMember): ChatRoomMember = participants.save(p)

    /** 벌크 UPDATE 라 트랜잭션이 있어야 한다. 호출부가 트랜잭션 안이면 그대로 참여한다. */
    @Transactional
    override fun increaseUnread(roomId: Long, memberId: Long) {
        participants.increaseUnread(roomId, memberId)
    }

    @Transactional
    override fun rejoinParticipant(roomId: Long, memberId: Long) {
        participants.rejoin(roomId, memberId)
    }

    /**
     * 방 + 상대 id + 안 읽은 수를 한 쿼리로 가져온다.
     * 안 읽은 수는 내 참여자 행의 카운터를 그대로 읽는다 — 메시지가 Mongo 에 있어 세는 건 불가능하고,
     * 방마다 집계를 돌리면 목록 길이만큼 왕복이 늘어난다.
     *
     * 나간 방(leftAt) 도 거르지 않는다 — 재입장 정책이라 상대가 다시 보내면 방이 되살아나야 한다.
     * 아직 메시지가 없는 방은 lastMessageAt 이 null 이라 맨 뒤로 보낸다(nullsLast).
     */
    override fun findRoomSummaries(memberId: Long, size: Int, cursor: Instant?): List<ChatRoomSummary> {
        val mine = QChatRoomMember("mine")
        val partner = QChatRoomMember("partner")

        return dsl.select(QChatRoom, partner.memberId, mine.unreadCount)
            .from(mine)
            .join(QChatRoom).on(QChatRoom.id.eq(mine.roomId))
            .join(partner).on(partner.roomId.eq(mine.roomId), partner.memberId.ne(memberId))
            .where(mine.memberId.eq(memberId), cursor?.let(QChatRoom.lastMessageAt::lt))
            .orderBy(QChatRoom.lastMessageAt.desc().nullsLast())
            .limit(size.toLong())
            .fetch()
            .map { ChatRoomSummary(it.get(QChatRoom)!!, it.get(partner.memberId)!!, it.get(mine.unreadCount)!!) }
    }
}

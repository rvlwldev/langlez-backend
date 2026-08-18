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
     * 1:1 방은 참여자 두 행으로만 식별된다. 방에 memberA/memberB 컬럼을 두면
     * (a,b) 와 (b,a) 를 정규화해 넣어야 하고 한 곳이라도 빼먹으면 방이 두 개 생긴다.
     * 참여자 테이블을 두 번 조인하면 인자 순서와 무관해진다.
     */
    override fun findRoomBetween(a: Long, b: Long): ChatRoom? {
        val one = QChatRoomMember("one")
        val other = QChatRoomMember("other")

        return dsl.selectFrom(QChatRoom)
            .join(one).on(one.roomId.eq(QChatRoom.id), one.memberId.eq(a))
            .join(other).on(other.roomId.eq(QChatRoom.id), other.memberId.eq(b))
            .fetchFirst()
    }

    override fun createRoom(a: Long, b: Long): ChatRoom = rooms.save(ChatRoom()).also { room ->
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

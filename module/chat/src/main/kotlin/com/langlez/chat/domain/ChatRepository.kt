package com.langlez.chat.domain

import java.time.Instant

/** 방·참여자 저장소 포트(Postgres). 메시지 본문은 `ChatMessageRepository`(Mongo) 가 갖는다. */
interface ChatRepository {

    fun findRoomBetween(a: Long, b: Long): ChatRoom?
    fun createRoom(a: Long, b: Long): ChatRoom
    fun findRoom(roomId: Long): ChatRoom?

    fun findParticipant(roomId: Long, memberId: Long): ChatRoomMember?
    fun findParticipants(roomId: Long): List<ChatRoomMember>
    fun saveParticipant(p: ChatRoomMember): ChatRoomMember

    /**
     * 안 읽은 수 +1.
     *
     * 엔티티에 읽고-쓰기로 올리면 같은 방으로 동시에 여러 건이 들어올 때 증가가 유실된다.
     * (A 가 연속 전송하면 상대 행을 여러 요청이 동시에 건드린다)
     * DB 에서 단일 UPDATE 로 더해야 값이 정확하다.
     */
    fun increaseUnread(roomId: Long, memberId: Long)

    /** 마지막 메시지 최신순. 나간 방도 상대가 보내면 재등장하므로 leftAt 필터 안 함 */
    fun findRoomSummaries(memberId: Long, size: Int, cursor: Instant?): List<ChatRoomSummary>
}

data class ChatRoomSummary(val room: ChatRoom, val partnerId: Long, val unreadCount: Long)

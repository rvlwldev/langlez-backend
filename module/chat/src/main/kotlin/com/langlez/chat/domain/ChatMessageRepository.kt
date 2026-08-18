package com.langlez.chat.domain

import java.time.Instant

/** 메시지 저장소 포트. 방·참여자는 Postgres(`ChatRepository`), 메시지 본문은 여기(Mongo)다. */
interface ChatMessageRepository {

    /** 방별 단조 증가 번호. 정렬·커서의 유일한 기준이라 이 값이 뒤로 가면 대화 순서가 통째로 뒤집힌다. */
    fun nextSeq(roomId: Long): Long

    fun save(message: ChatMessage): ChatMessage

    fun find(id: String): ChatMessage?

    /** seq 내림차순 커서 페이징. 커서는 직전 페이지 마지막 메시지의 seq. */
    fun findByRoom(roomId: Long, size: Int, cursor: Long?): List<ChatMessage>

    /** 알림을 아직 발행하지 않은 메시지. `published` 플래그가 아웃박스 역할을 한다. */
    fun findUnpublished(limit: Int): List<ChatMessage>


    /** [since] 이후에 메시지가 들어온 방. 대사(reconciliation) 대상을 최근에 움직인 방으로만 좁힌다. */
    fun findRoomIdsSince(since: Instant): List<Long>

    /**
     * [memberId] 가 아직 안 읽은 메시지 수. 카운터가 어긋났을 때 다시 세는 용도다.
     *
     * 자기가 보낸 메시지는 세지 않는다 — 전송 때 카운터가 오르는 쪽은 받는 사람뿐이라,
     * 여기서 같이 세면 대사가 돌 때마다 보낸 사람 배지에 자기 메시지가 얹힌다.
     * [lastReadAt] 이 null 이면 한 번도 안 읽은 방이라 전부가 안 읽은 메시지다.
     */
    fun countUnread(roomId: Long, memberId: Long, lastReadAt: Instant?): Long
}

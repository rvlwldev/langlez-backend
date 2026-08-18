package com.langlez.wave.domain

/**
 * 라이브 세션 저장소 포트(레디스).
 *
 * 음성방의 대화는 방과 함께 끝난다. Postgres 도 Mongo 도 쓰지 않고 방별 링버퍼에 최근 N 개만 남긴다.
 * 지금 누가 방에 있는지도 같은 성질이다 — 서버가 죽으면 통화도 끊기므로 영속할 이유가 없다.
 */
interface WaveSessionRepository {

    fun join(roomId: Long, memberId: Long)
    fun leave(roomId: Long, memberId: Long)

    fun participants(roomId: Long): Set<Long>
    fun isParticipant(roomId: Long, memberId: Long): Boolean

    /** 오래된 것부터 밀어내며 최근 N 개만 남긴다. */
    fun appendChat(roomId: Long, chat: WaveChat)

    /** 오래된 순. 늦게 들어온 사람이 흐름을 따라잡을 만큼만 남아 있다. */
    fun recentChats(roomId: Long): List<WaveChat>

    /** 방이 끝났다. 대화와 참여자를 즉시 지운다. */
    fun clear(roomId: Long)
}

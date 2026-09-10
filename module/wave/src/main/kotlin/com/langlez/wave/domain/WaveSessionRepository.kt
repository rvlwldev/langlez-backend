package com.langlez.wave.domain

/**
 * 라이브 세션 저장소 포트(레디스).
 *
 * 음성방의 대화는 방과 함께 끝난다. Postgres 도 Mongo 도 쓰지 않고 방별 링버퍼에 최근 N 개만 남긴다.
 * 지금 누가 방에 있는지도 같은 성질이다 — 서버가 죽으면 통화도 끊기므로 영속할 이유가 없다.
 */
interface WaveSessionRepository {

    fun join(roomId: Long, memberId: Long)

    /**
     * 정원이 남아 있을 때만 참여자로 넣는다. 이미 참여 중이면 아무것도 바꾸지 않고 `true`.
     *
     * 정원 검사와 등록이 **한 번의 원자 연산**이어야 한다. 둘로 갈리면 두 사람이 동시에
     * 마지막 자리를 가져간다. 분산 락으로 감싸는 방법도 있지만 그쪽은 락을 못 잡았을 때
     * 조용히 넘어가는 경로가 생기고, 그러면 사용자는 성공을 받고도 참여자가 아니게 된다.
     *
     * @return 정원에 들어갔으면 `true`, 방이 이미 찼으면 `false`
     */
    fun joinIfNotFull(roomId: Long, memberId: Long, maxParticipants: Int): Boolean

    fun leave(roomId: Long, memberId: Long)

    fun participants(roomId: Long): Set<Long>
    fun participantCount(roomId: Long): Int
    fun isParticipant(roomId: Long, memberId: Long): Boolean

    /** 오래된 것부터 밀어내며 최근 N 개만 남긴다. */
    fun appendChat(roomId: Long, chat: WaveChat)

    /** 오래된 순. 늦게 들어온 사람이 흐름을 따라잡을 만큼만 남아 있다. */
    fun recentChats(roomId: Long): List<WaveChat>

    /** 방이 끝났다. 대화와 참여자를 즉시 지운다. */
    fun clear(roomId: Long)
}

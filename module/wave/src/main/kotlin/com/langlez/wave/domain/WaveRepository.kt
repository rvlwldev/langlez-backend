package com.langlez.wave.domain

import java.time.Instant

/** 방 생명주기 저장소 포트(Postgres). 대화·참여자는 `WaveSessionRepository` 가 갖는다. */
interface WaveRepository {

    fun save(room: WaveRoom): WaveRoom

    fun find(id: Long): WaveRoom?

    /** 진행 중인 방만 최신순. 커서는 직전 페이지 마지막 방의 id (서버 시계가 어긋나도 순서가 뒤집히지 않는다). */
    fun findAllOpen(size: Int, cursor: Long?): List<WaveRoom>

    /**
     * 리퍼용. 진행 중인 방을 **오래된 것부터** 준다.
     *
     * 목록 조회([findAllOpen])와 방향이 반대인 건 실수가 아니다. 유령 방은 오래된 쪽에 쌓이고,
     * 한 주기에 [limit] 건만 처리하므로 최신순으로 훑으면 정작 치워야 할 것에 영영 못 닿는다.
     */
    fun findOpenStartedBefore(startedBefore: Instant, limit: Int): List<WaveRoom>
}

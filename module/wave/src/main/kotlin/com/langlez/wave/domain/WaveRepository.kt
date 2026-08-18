package com.langlez.wave.domain

/** 방 생명주기 저장소 포트(Postgres). 대화·참여자는 `WaveSessionRepository` 가 갖는다. */
interface WaveRepository {

    fun save(room: WaveRoom): WaveRoom

    fun find(id: Long): WaveRoom?

    /** 진행 중인 방만 최신순. 커서는 직전 페이지 마지막 방의 id (서버 시계가 어긋나도 순서가 뒤집히지 않는다). */
    fun findAllOpen(size: Int, cursor: Long?): List<WaveRoom>
}

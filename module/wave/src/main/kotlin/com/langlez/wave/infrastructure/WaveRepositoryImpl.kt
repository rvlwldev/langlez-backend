package com.langlez.wave.infrastructure

import com.langlez.wave.domain.WaveRepository
import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.infrastructure.jpa.WaveRoomJpaRepository
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

/**
 * 방 저장소 어댑터.
 *
 * 캐시를 두지 않는다. 방은 열려 있는 동안 계속 바뀌고 목록도 짧아 캐시가 맞을 틈이 없다.
 */
@Repository
class WaveRepositoryImpl(private val jpa: WaveRoomJpaRepository) : WaveRepository {

    override fun save(room: WaveRoom): WaveRoom = jpa.save(room)

    override fun find(id: Long): WaveRoom? = jpa.findByIdOrNull(id)

    // 첫 페이지는 커서가 없다. 상한값으로 시작하면 분기 없이 같은 쿼리 하나로 끝난다.
    override fun findAllOpen(size: Int, cursor: Long?): List<WaveRoom> =
        jpa.findAllByEndedAtIsNullAndIdLessThanOrderByIdDesc(cursor ?: Long.MAX_VALUE, Pageable.ofSize(size))
}

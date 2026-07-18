package com.langlez.wave.infrastructure

import com.langlez.wave.domain.WaveRoom
import com.langlez.wave.domain.WaveRoomRepository
import com.langlez.wave.infrastructure.jpa.WaveRoomJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class WaveRoomRepositoryImpl(
    private val waveRoomJpa: WaveRoomJpaRepository,
) : WaveRoomRepository {

    override fun save(room: WaveRoom): WaveRoom = waveRoomJpa.save(room)

    override fun findById(id: Long): WaveRoom? = waveRoomJpa.findByIdOrNull(id)

    override fun findActive(cursor: Long?, size: Int): List<WaveRoom> =
        waveRoomJpa.findActive(cursor, PageRequest.of(0, size))
}

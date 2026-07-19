package com.langlez.wavechat.infrastructure

import com.langlez.wavechat.domain.WaveMessage
import com.langlez.wavechat.domain.WaveMessageRepository
import com.langlez.wavechat.infrastructure.jpa.WaveMessageJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class WaveMessageRepositoryImpl(
    private val waveMessageJpaRepository: WaveMessageJpaRepository,
) : WaveMessageRepository {

    override fun save(message: WaveMessage): WaveMessage =
        waveMessageJpaRepository.save(message)

    override fun findByRoom(waveRoomId: Long, cursor: Long?, size: Int): List<WaveMessage> =
        waveMessageJpaRepository.findByRoom(waveRoomId, cursor, PageRequest.of(0, size))

    override fun findById(id: Long): WaveMessage? =
        waveMessageJpaRepository.findByIdOrNull(id)

    override fun markDeleted(id: Long) {
        waveMessageJpaRepository.markDeleted(id)
    }
}

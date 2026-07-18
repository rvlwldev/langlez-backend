package com.langlez.wave.infrastructure.jpa

import com.langlez.wave.domain.WaveRoom
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface WaveRoomJpaRepository : JpaRepository<WaveRoom, Long> {
    @Query("SELECT r FROM WaveRoom r WHERE r.endedAt IS NULL AND (:cursor IS NULL OR r.id < :cursor) ORDER BY r.id DESC")
    fun findActive(cursor: Long?, pageable: PageRequest): List<WaveRoom>
}

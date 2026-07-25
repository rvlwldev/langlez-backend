package com.langlez.wavechat.infrastructure.jpa

import com.langlez.wavechat.domain.WaveMessage
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface WaveMessageJpaRepository : JpaRepository<WaveMessage, Long> {

    @Query("SELECT m FROM WaveMessage m WHERE m.waveRoomId = :waveRoomId AND (:cursor IS NULL OR m.id > :cursor) ORDER BY m.id ASC")
    fun findByRoom(
        @Param("waveRoomId") waveRoomId: Long,
        @Param("cursor") cursor: Long?,
        pageable: Pageable
    ): List<WaveMessage>
}

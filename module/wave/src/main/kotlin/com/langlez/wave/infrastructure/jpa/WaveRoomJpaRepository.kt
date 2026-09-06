package com.langlez.wave.infrastructure.jpa

import com.langlez.wave.domain.WaveRoom
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import java.time.Instant

interface WaveRoomJpaRepository : JpaRepository<WaveRoom, Long> {

    /** 조건이 "진행 중 + 커서" 둘뿐이라 QueryDSL 을 끌어오는 것보다 파생 쿼리가 짧다. */
    fun findAllByEndedAtIsNullAndIdLessThanOrderByIdDesc(cursor: Long, pageable: Pageable): List<WaveRoom>

    /** 위와 같은 이유로 파생 쿼리다. 조건은 "진행 중 + 개설 시각" 둘뿐이다. */
    fun findAllByEndedAtIsNullAndStartedAtLessThanOrderByIdAsc(
        startedBefore: Instant,
        pageable: Pageable,
    ): List<WaveRoom>
}

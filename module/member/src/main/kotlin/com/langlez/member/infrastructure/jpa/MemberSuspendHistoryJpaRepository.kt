package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.MemberSuspendHistory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface MemberSuspendHistoryJpaRepository : JpaRepository<MemberSuspendHistory, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update MemberSuspendHistory h set h.isReleased = true where h.memberId = :memberId and h.isReleased = false")
    fun releaseActive(@Param("memberId") memberId: Long)
}

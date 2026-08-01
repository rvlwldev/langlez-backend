package com.langlez.member.infrastructure.jpa

import com.langlez.member.infrastructure.MemberOutBox
import com.langlez.rdb.outbox.OutBoxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutBoxJpaRepository : JpaRepository<MemberOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(statuses: List<OutBoxStatus>, pageable: Pageable): List<MemberOutBox>
    fun findAllByStatusIn(statuses: List<OutBoxStatus>, pageable: Pageable): List<MemberOutBox>
}

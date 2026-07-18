package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.MemberOutBox
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutBoxJpaRepository : JpaRepository<MemberOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<MemberOutBox.Status>,
        pageable: org.springframework.data.domain.Pageable,
    ): List<MemberOutBox>
    fun findAllByStatus(status: MemberOutBox.Status): List<MemberOutBox>
}

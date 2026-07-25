package com.langlez.relationship.infrastructure.outbox.jpa

import com.langlez.relationship.infrastructure.outbox.RelationshipOutBox
import com.langlez.mysql.outbox.OutBoxStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface RelationshipOutBoxJpaRepository : JpaRepository<RelationshipOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<OutBoxStatus>,
        pageable: Pageable,
    ): List<RelationshipOutBox>

    fun findAllByStatusIn(statuses: List<OutBoxStatus>, pageable: Pageable): List<RelationshipOutBox>
}

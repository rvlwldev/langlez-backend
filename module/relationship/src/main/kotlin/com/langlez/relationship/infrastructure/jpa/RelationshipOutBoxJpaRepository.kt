package com.langlez.relationship.infrastructure.jpa

import com.langlez.relationship.domain.RelationshipOutBox
import org.springframework.data.jpa.repository.JpaRepository

interface RelationshipOutBoxJpaRepository : JpaRepository<RelationshipOutBox, Long> {
    fun findAllByStatusInOrderByCreatedAtAsc(
        statuses: List<RelationshipOutBox.Status>,
        pageable: org.springframework.data.domain.Pageable,
    ): List<RelationshipOutBox>
    fun findAllByStatus(status: RelationshipOutBox.Status): List<RelationshipOutBox>
}

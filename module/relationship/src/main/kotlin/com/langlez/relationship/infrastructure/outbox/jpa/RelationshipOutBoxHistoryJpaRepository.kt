package com.langlez.relationship.infrastructure.outbox.jpa

import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface RelationshipOutBoxHistoryJpaRepository : JpaRepository<RelationshipOutBoxHistory, Long>

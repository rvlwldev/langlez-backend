package com.langlez.relationship.infrastructure.jpa

import com.langlez.relationship.domain.RelationshipOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface RelationshipOutBoxHistoryJpaRepository : JpaRepository<RelationshipOutBoxHistory, Long>

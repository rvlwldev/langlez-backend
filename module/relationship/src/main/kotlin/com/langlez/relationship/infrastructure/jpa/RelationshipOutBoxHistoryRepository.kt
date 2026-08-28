package com.langlez.relationship.infrastructure.jpa

import com.langlez.rdb.outbox.OutBoxHistoryRepository
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBoxHistory
import org.springframework.stereotype.Repository

@Repository
interface RelationshipOutBoxHistoryRepository : OutBoxHistoryRepository<RelationshipOutBoxHistory>

package com.langlez.relationship.infrastructure.jpa

import com.langlez.rdb.outbox.OutBoxRepository
import com.langlez.relationship.infrastructure.outbox.RelationshipOutBox
import org.springframework.stereotype.Repository

@Repository
interface RelationshipOutBoxRepository : OutBoxRepository<RelationshipOutBox>

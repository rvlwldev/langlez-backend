package com.langlez.relationship.infrastructure.outbox

import com.langlez.rdb.outbox.OutBoxRepository

interface RelationshipOutBoxRepository : OutBoxRepository<RelationshipOutBox, RelationshipOutBoxHistory>

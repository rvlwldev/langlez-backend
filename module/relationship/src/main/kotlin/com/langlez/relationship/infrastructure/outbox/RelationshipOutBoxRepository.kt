package com.langlez.relationship.infrastructure.outbox

import com.langlez.mysql.outbox.OutBoxRepository

interface RelationshipOutBoxRepository : OutBoxRepository<RelationshipOutBox, RelationshipOutBoxHistory>

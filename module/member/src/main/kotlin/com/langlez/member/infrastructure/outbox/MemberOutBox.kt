package com.langlez.member.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "member_event_outbox")
class MemberOutBox(
    domain: String,
    topic: String,
    payload: String,
    key: String? = null
) : OutBox(domain, topic, payload, key)

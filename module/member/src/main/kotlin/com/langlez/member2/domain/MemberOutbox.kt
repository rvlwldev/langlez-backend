package com.langlez.member2.domain

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "member_outboxes")
class MemberOutbox(
    domain: String = "MEMBER",
    topic: String,
    payload: String,
    key: Long? = null
) : OutBox(domain, topic, payload, key)

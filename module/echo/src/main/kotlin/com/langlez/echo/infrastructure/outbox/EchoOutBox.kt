package com.langlez.echo.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import jakarta.persistence.Entity
import jakarta.persistence.Table

@Entity
@Table(name = "echo_event_outbox")
class EchoOutBox(
    domain: String,
    topic: String,
    payload: String,
    key: String? = null
) : OutBox(domain, topic, payload, key)

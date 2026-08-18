package com.langlez.echo.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(name = "echo_event_outbox_history", indexes = [Index(name = "IDX_ECHO_OUTBOX_DOMAIN", columnList = "domain")])
class EchoOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)

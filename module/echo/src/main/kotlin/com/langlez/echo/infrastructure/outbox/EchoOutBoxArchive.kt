package com.langlez.echo.infrastructure.outbox

import com.langlez.mysql.outbox.OutBoxArchive
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "echo_event_outbox_archive")
class EchoOutBoxArchive(
    domain: String,
    date: LocalDate,
    index: Int,
    count: Int,
    data: String,
) : OutBoxArchive(domain, date, index, count, data)

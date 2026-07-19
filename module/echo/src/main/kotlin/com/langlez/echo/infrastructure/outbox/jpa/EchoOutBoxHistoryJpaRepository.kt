package com.langlez.echo.infrastructure.outbox.jpa

import com.langlez.echo.infrastructure.outbox.EchoOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface EchoOutBoxHistoryJpaRepository : JpaRepository<EchoOutBoxHistory, Long>

package com.langlez.echo.infrastructure.outbox.jpa

import com.langlez.echo.infrastructure.outbox.EchoOutBoxArchive
import org.springframework.data.jpa.repository.JpaRepository

interface EchoOutBoxArchiveJpaRepository : JpaRepository<EchoOutBoxArchive, Long>

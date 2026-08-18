package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.infrastructure.outbox.EchoOutBox
import com.langlez.rdb.outbox.OutBoxRepository
import org.springframework.stereotype.Repository

@Repository
interface EchoOutBoxRepository : OutBoxRepository<EchoOutBox>

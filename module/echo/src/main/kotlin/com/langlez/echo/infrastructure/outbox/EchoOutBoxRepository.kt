package com.langlez.echo.infrastructure.outbox

import com.langlez.rdb.outbox.OutBoxRepository

interface EchoOutBoxRepository : OutBoxRepository<EchoOutBox, EchoOutBoxHistory>

package com.langlez.echo.infrastructure.outbox

import com.langlez.mysql.outbox.OutBoxRepository

interface EchoOutBoxRepository : OutBoxRepository<EchoOutBox, EchoOutBoxHistory>

package com.langlez.echo.infrastructure.outbox

import com.langlez.mysql.outbox.OutBoxArchiveRepository

interface EchoOutBoxArchiveRepository : OutBoxArchiveRepository<EchoOutBoxHistory, EchoOutBoxArchive>

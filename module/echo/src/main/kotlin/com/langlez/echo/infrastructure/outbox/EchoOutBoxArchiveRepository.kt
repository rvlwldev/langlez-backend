package com.langlez.echo.infrastructure.outbox

import com.langlez.rdb.outbox.OutBoxArchiveRepository

interface EchoOutBoxArchiveRepository : OutBoxArchiveRepository<EchoOutBoxHistory, EchoOutBoxArchive>

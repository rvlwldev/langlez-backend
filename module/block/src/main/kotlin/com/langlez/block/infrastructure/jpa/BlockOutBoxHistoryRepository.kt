package com.langlez.block.infrastructure.jpa

import com.langlez.block.infrastructure.outbox.BlockOutBoxHistory
import com.langlez.rdb.outbox.OutBoxHistoryRepository
import org.springframework.stereotype.Repository

@Repository
interface BlockOutBoxHistoryRepository : OutBoxHistoryRepository<BlockOutBoxHistory>

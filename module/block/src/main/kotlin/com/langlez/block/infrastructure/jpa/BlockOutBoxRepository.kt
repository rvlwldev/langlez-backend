package com.langlez.block.infrastructure.jpa

import com.langlez.block.infrastructure.outbox.BlockOutBox
import com.langlez.rdb.outbox.OutBoxRepository
import org.springframework.stereotype.Repository

@Repository
interface BlockOutBoxRepository : OutBoxRepository<BlockOutBox>

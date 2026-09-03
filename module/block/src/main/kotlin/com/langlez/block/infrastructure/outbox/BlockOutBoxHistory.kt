package com.langlez.block.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "block_event_outbox_history",
    indexes = [Index(name = "IDX_BLOCK_OUTBOX_DOMAIN", columnList = "domain")]
)
class BlockOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)

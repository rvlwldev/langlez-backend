package com.langlez.follow.infrastructure.outbox

import com.langlez.rdb.outbox.OutBox
import com.langlez.rdb.outbox.OutBoxHistory
import jakarta.persistence.Entity
import jakarta.persistence.Index
import jakarta.persistence.Table

@Entity
@Table(
    name = "follow_event_outbox_history",
    indexes = [Index(name = "IDX_FOLLOW_OUTBOX_DOMAIN", columnList = "domain")]
)
class FollowOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)

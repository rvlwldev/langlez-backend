package com.langlez.follow.infrastructure.jpa

import com.langlez.follow.infrastructure.outbox.FollowOutBoxHistory
import com.langlez.rdb.outbox.OutBoxHistoryRepository
import org.springframework.stereotype.Repository

@Repository
interface FollowOutBoxHistoryRepository : OutBoxHistoryRepository<FollowOutBoxHistory>

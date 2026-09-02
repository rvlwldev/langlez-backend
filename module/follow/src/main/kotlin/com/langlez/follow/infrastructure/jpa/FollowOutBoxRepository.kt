package com.langlez.follow.infrastructure.jpa

import com.langlez.follow.infrastructure.outbox.FollowOutBox
import com.langlez.rdb.outbox.OutBoxRepository
import org.springframework.stereotype.Repository

@Repository
interface FollowOutBoxRepository : OutBoxRepository<FollowOutBox>

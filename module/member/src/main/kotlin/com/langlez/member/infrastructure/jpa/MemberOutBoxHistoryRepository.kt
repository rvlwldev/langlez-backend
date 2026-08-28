package com.langlez.member.infrastructure.jpa

import com.langlez.member.infrastructure.outbox.MemberOutBoxHistory
import com.langlez.rdb.outbox.OutBoxHistoryRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberOutBoxHistoryRepository : OutBoxHistoryRepository<MemberOutBoxHistory>

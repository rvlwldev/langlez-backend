package com.langlez.member.infrastructure.jpa

import com.langlez.member.infrastructure.outbox.MemberOutBox
import com.langlez.rdb.outbox.OutBoxRepository
import org.springframework.stereotype.Repository

@Repository
interface MemberOutBoxRepository : OutBoxRepository<MemberOutBox>
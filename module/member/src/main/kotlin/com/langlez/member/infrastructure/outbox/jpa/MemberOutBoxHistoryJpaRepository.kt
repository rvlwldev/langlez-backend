package com.langlez.member.infrastructure.outbox.jpa

import com.langlez.member.infrastructure.outbox.MemberOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutBoxHistoryJpaRepository : JpaRepository<MemberOutBoxHistory, Long>

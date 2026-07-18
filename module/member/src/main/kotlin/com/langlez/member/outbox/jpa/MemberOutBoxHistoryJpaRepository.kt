package com.langlez.member.outbox.jpa

import com.langlez.member.outbox.MemberOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutBoxHistoryJpaRepository : JpaRepository<MemberOutBoxHistory, Long>

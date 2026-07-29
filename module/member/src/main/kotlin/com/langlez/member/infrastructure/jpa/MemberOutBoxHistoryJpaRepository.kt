package com.langlez.member.infrastructure.jpa

import com.langlez.member.infrastructure.MemberOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutBoxHistoryJpaRepository : JpaRepository<MemberOutBoxHistory, Long>

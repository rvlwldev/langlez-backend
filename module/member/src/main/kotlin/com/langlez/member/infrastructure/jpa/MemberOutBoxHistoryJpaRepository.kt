package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.MemberOutBoxHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MemberOutBoxHistoryJpaRepository : JpaRepository<MemberOutBoxHistory, Long>

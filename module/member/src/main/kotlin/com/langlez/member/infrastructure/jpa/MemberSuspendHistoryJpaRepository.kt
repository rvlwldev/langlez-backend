package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.MemberSuspendHistory
import org.springframework.data.jpa.repository.JpaRepository

interface MemberSuspendHistoryJpaRepository : JpaRepository<MemberSuspendHistory, Long>

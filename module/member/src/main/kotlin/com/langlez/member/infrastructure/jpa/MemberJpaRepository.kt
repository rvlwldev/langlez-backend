package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<Member, Long>
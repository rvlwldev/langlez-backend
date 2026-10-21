package com.langlez.member2.infrastructure.jpa

import com.langlez.member2.domain.Member
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpa : JpaRepository<Member, Long>


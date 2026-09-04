package com.langlez.lang.infrastructure.jpa

import com.langlez.lang.domain.MemberLanguage
import org.springframework.data.jpa.repository.JpaRepository

interface MemberLanguageJpaRepository : JpaRepository<MemberLanguage, Long> {

    fun findAllByMemberId(memberId: Long): List<MemberLanguage>

    fun findAllByMemberIdIn(memberIds: Collection<Long>): List<MemberLanguage>
}

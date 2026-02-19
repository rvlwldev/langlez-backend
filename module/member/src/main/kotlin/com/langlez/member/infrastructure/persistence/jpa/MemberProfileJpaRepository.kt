package com.langlez.member.infrastructure.persistence.jpa

import com.langlez.member.domain.MemberProfile
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional

interface MemberProfileJpaRepository : JpaRepository<MemberProfile, Long> {
    @EntityGraph(attributePaths = ["languages"])
    override fun findById(id: Long): Optional<MemberProfile>
}

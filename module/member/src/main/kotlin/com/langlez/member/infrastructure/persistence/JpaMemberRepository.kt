package com.langlez.member.infrastructure.persistence

import com.langlez.member.domain.Member
import com.langlez.member.domain.embedded.MemberProvider
import java.time.Instant
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface JpaMemberRepository : JpaRepository<Member, Long> {
        fun findByEmail(email: String): Member?
        fun findByHandle(handle: String): Member?
        fun existsByHandle(handle: String): Boolean

        @Query(
                "SELECT m FROM Member m WHERE m.provider.id = :providerId AND m.provider.type = :providerType"
        )
        fun findByProviderIdAndProviderType(
                providerId: String,
                providerType: MemberProvider.Type
        ): Member?

        /** 초기화 미완료(init=false)이고 생성 시간이 threshold보다 오래된 Member 조회 */
        @Query("SELECT m FROM Member m WHERE m.init = false AND m.audit.createdAt < :threshold")
        fun findIncompleteOlderThan(threshold: Instant): List<Member>
}

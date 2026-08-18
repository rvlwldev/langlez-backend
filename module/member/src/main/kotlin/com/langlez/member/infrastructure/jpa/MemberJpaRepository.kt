package com.langlez.member.infrastructure.jpa

import com.langlez.member.domain.Member
import org.springframework.data.jpa.repository.EntityGraph
import org.springframework.data.jpa.repository.JpaRepository

interface MemberJpaRepository : JpaRepository<Member, Long> {

    /**
     * `open-in-view: false` 라 조회 트랜잭션이 끝나면 LAZY 연관을 못 읽는다.
     * `MemberMeResponse` 가 `member.audit.lastAccessedAt` 을 읽으므로 audit 을 함께 가져와야 한다.
     * 안 그러면 응답 조립 중 LazyInitializationException 이 나고,
     * 캐시에 넣을 때는 초기화 안 된 프록시가 직렬화되면서 캐시 전체가 죽은 것으로 오판된다.
     */
    @EntityGraph(attributePaths = ["audit"])
    fun findWithAuditById(id: Long): Member?

    @EntityGraph(attributePaths = ["audit"])
    fun findAllWithAuditByIdIn(ids: Collection<Long>): List<Member>
}

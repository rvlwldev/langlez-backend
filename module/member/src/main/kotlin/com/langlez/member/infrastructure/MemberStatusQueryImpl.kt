package com.langlez.member.infrastructure

import com.langlez.core.MemberStatusQuery
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.stereotype.Repository

/**
 * 계정 상태 조회 포트 구현.
 *
 * `JwtAuthenticationFilter` 가 매 요청 부른다. `MemberRepository.find` 는 2단계 캐시를 타고
 * 상태를 바꾸는 경로(`suspend`/`unsuspend`/`withdraw`)가 전부 `repo.save` 를 거쳐
 * 캐시를 갱신하므로, 정상 상황에서 요청당 DB 왕복은 생기지 않는다.
 */
@Repository
class MemberStatusQueryImpl(private val repo: MemberRepository) : MemberStatusQuery {

    override fun findStatus(memberId: Long): MemberStatusQuery.Status? = when (repo.find(memberId)?.status) {
        Member.Status.CREATED -> MemberStatusQuery.Status.CREATED
        Member.Status.ACTIVE -> MemberStatusQuery.Status.ACTIVE
        Member.Status.SUSPENDED -> MemberStatusQuery.Status.SUSPENDED
        Member.Status.WITHDRAWN -> MemberStatusQuery.Status.WITHDRAWN
        null -> null
    }
}

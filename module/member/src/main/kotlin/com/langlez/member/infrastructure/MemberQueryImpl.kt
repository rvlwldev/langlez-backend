package com.langlez.member.infrastructure

import com.langlez.core.MemberQuery
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.stereotype.Repository

/** 회원 계정 조회 포트 구현. */
@Repository
class MemberQueryImpl(private val repo: MemberRepository) : MemberQuery {

    override fun findIdByHandle(handle: String): Long? = repo.find(handle)?.id

    override fun findProfileInfo(memberId: Long): MemberQuery.ProfileInfo? = repo.find(memberId)?.toProfileInfo()

    override fun findProfileInfos(memberIds: Collection<Long>): Map<Long, MemberQuery.ProfileInfo> {
        if (memberIds.isEmpty()) return emptyMap()
        return repo.findAll(memberIds.toSet()).associate { it.id to it.toProfileInfo() }
    }

    private fun Member.toProfileInfo() = MemberQuery.ProfileInfo(
        id = id,
        handle = handle,
        gender = gender.name,
        locale = locale,
        birthDay = birthDay,
    )
}

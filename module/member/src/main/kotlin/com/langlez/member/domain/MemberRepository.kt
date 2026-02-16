package com.langlez.member.domain

import com.langlez.member.domain.embedded.MemberProvider
import java.time.Instant

interface MemberRepository {
    fun save(member: Member): Member

    fun findById(id: Long): Member?

    fun findByEmail(email: String): Member?

    fun findByHandle(handle: String): Member?

    fun findByProvider(id: String, type: MemberProvider.Type): Member?

    fun existsByHandle(handle: String): Boolean

    fun delete(member: Member)

    fun deleteAll(members: List<Member>)
    
    /** 초기화 미완료(init=false)이고 생성 시간이 threshold보다 오래된 Member 조회 */
    fun findAllIncompleteOlderThan(threshold: Instant): List<Member>
}

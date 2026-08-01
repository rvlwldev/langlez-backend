package com.langlez.member.infrastructure

import com.langlez.member.application.MemberRepository
import com.langlez.member.application.MemberRepository2
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import org.springframework.stereotype.Repository

/**
 * 구 인터페이스 유지용 위임 계층. 캐시 로직은 `MemberRepositoryImpl2` 하나에만 있다.
 *
 * 다른 모듈이 각자 `MemberRepository2` 로 갈아탄 뒤 이 클래스와 `MemberRepository` 를 지운다.
 */
@Repository
class MemberRepositoryImpl(
    private val delegate: MemberRepository2,
) : MemberRepository {

    override fun save(member: Member): Member = delegate.save(member)

    override fun findById(id: Long): Member? = delegate.find(id)
    override fun findByIds(ids: List<Long>): List<Member> = delegate.findAll(ids)
    override fun findByEmail(email: String): Member? = delegate.findByEmail(email)
    override fun findByUsername(username: String): Member? = delegate.find(username)
    override fun findByUsernames(usernames: List<String>): List<Member> = delegate.findAllByUsernames(usernames)
    override fun findByProvider(type: MemberProvider, id: String): Member? = delegate.find(type, id)
    override fun findAll(size: Int, cursor: Long?): List<Member> = delegate.findAll(size, cursor)

    override fun countAll(): Long = delegate.count()

    override fun deleteAll(members: List<Member>) = delegate.delete(members)
}

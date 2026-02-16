package com.langlez.member.application

import com.langlez.common.exception.LanglezException
import com.langlez.member.application.command.CreateMemberCommand
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * Member 일반 서비스
 * - OAuth 로그인 시 Member 조회/생성
 * - Member 조회
 */
@Service
class MemberService(private val repo: MemberRepository) {

    /** OAuth 로그인 시 Member 조회 또는 생성 */
    @Transactional
    fun findOrCreateMember(command: CreateMemberCommand): Member = with(command) {
        val member = repo.findByProvider(providerId, providerType) ?: repo.findByEmail(email)
        if (member != null) return member.apply { login() }

        // 신규 회원 생성
        Member.create(nickname, email, providerId, providerType.name, providerUserName)
            .apply { login(); repo.save(this) }
    }

    @Transactional(readOnly = true)
    fun getMember(email: String): Member =
        repo.findByEmail(email) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")

    @Transactional(readOnly = true)
    fun getMemberById(id: Long): Member =
        repo.findById(id) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")

    @Transactional(readOnly = true)
    fun getMemberByHandle(handle: String): Member =
        repo.findByHandle(handle) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
}

package com.langlez.member.application

import com.langlez.common.exception.LanglezException
import com.langlez.member.application.command.CreateMemberCommand
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(private val repo: MemberRepository) {

    /** OAuth 로그인 시 Member 조회 또는 생성 */
    @Transactional
    suspend fun findOrCreateMember(command: CreateMemberCommand): Member = withContext(Dispatchers.IO) {
        val member = repo.findByProvider(command.providerId, command.providerType) ?: repo.findByEmail(command.email)
        if (member != null) return@withContext member.apply { login() }

        // 신규 회원 생성
        Member.create(command.nickname, command.email, command.providerId, command.providerType.name, command.providerUserName)
            .apply { login(); repo.save(this) }
    }

    @Transactional(readOnly = true)
    suspend fun getMember(email: String): Member = withContext(Dispatchers.IO) {
        repo.findByEmail(email) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
    }

    @Transactional(readOnly = true)
    suspend fun getMemberById(id: Long): Member = withContext(Dispatchers.IO) {
        repo.findById(id) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
    }

    @Transactional(readOnly = true)
    suspend fun getMemberByHandle(handle: String): Member = withContext(Dispatchers.IO) {
        repo.findByHandle(handle) ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
    }
}

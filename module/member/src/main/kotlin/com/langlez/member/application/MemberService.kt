package com.langlez.member.application

import com.langlez.core.LanglezException
import com.langlez.member.application.MemberCommand.Create
import com.langlez.member.application.MemberCommand.Provider
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val repo: MemberRepository,
) {

    @Transactional
    @Retryable(maxAttempts = 3, backoff = Backoff(100), retryFor = [Exception::class])
    fun createMember(providerCmd: Provider, command: Create): Member {
        val member = Member(
            email = command.email,
            username = command.username,
            nickname = command.nickname,
            provider = providerCmd.type,
            providerId = providerCmd.id,
            providerDisplayName = providerCmd.username
        )

        member.login()
        return repo.save(member)
    }

    @Transactional
    fun updateUsername(id: Long, newUsername: String): Member {
        val member = repo.findById(id)
            ?: throw LanglezException(404, "member.not-found")

        if (!Member.isValidUsername(newUsername))
            throw LanglezException(400, "member.username.invalid")

        if (!member.canChangeUsername())
            throw LanglezException(400, "member.username.cooldown")

        if (newUsername != member.username && repo.findByUsername(newUsername) != null)
            throw LanglezException(409, "member.username.duplicated")

        member.changeUsername(newUsername)
        return repo.save(member)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.findById(id)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Member? = repo.findByEmail(email)

    @Transactional(readOnly = true)
    fun findByProvider(providerId: String, type: Member.Provider): Member? =
        repo.findByProvider(providerId, type)

    @Transactional
    fun updateLastAccess(id: Long) {
        val member = repo.findById(id) ?: return
        member.login()
        repo.save(member)
    }

    @Transactional
    fun updateNickname(id: Long, newNickname: String): Member {
        val member = repo.findById(id)
            ?: throw LanglezException(404, "member.not-found")

        if (!member.canChangeNickname())
            throw LanglezException(400, "member.nickname.cooldown")

        member.changeNickname(newNickname)
        return repo.save(member)
    }
}

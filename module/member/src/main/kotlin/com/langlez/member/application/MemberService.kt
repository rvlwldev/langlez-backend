package com.langlez.member.application

import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberCommand.Create
import com.langlez.member.application.MemberCommand.Provider
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import com.langlez.member.outbox.MemberOutBoxRepository
import org.springframework.http.HttpStatus.*
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(
    private val repo: MemberRepository,
    private val outbox: MemberOutBoxRepository,
) {

    @Transactional
    @Retryable(maxAttempts = 3, backoff = Backoff(100), retryFor = [Exception::class])
    fun createMember(providerCmd: Provider, command: Create): Member {
        val provider = MemberProvider(providerCmd.id, providerCmd.type, providerCmd.username)
        val member = Member(command.email, command.username, command.nickname, provider)

        member.login()
        val saved = repo.save(member)

        val event = MemberEvent.Created(saved.id, saved.email, saved.username, saved.nickname)
        outbox.save("MEMBER", saved.id.toString(), "member-created", event)

        return saved
    }

    @Transactional
    fun updateUsername(id: Long, newUsername: String): Member {
        val member = repo.findById(id)
            ?: throw LanglezException(NOT_FOUND, "member.not-found")

        if (!Member.isValidUsername(newUsername))
            throw LanglezException(BAD_REQUEST, "member.username.invalid")

        if (!member.canChangeUsername())
            throw LanglezException(BAD_REQUEST, "member.username.cooldown")

        if (newUsername != member.username && repo.findByUsername(newUsername) != null)
            throw LanglezException(CONFLICT, "member.username.duplicated")

        member.changeUsername(newUsername)
        return repo.save(member)
    }

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.findById(id)

    @Transactional(readOnly = true)
    fun findByEmail(email: String): Member? = repo.findByEmail(email)

    @Transactional(readOnly = true)
    fun findByProvider(providerId: String, type: com.langlez.member.domain.MemberProvider.Type): Member? =
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
            ?: throw LanglezException(NOT_FOUND, "member.not-found")

        if (!member.canChangeNickname())
            throw LanglezException(BAD_REQUEST, "member.nickname.cooldown")

        member.changeNickname(newNickname)
        return repo.save(member)
    }
}

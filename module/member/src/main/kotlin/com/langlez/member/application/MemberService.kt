package com.langlez.member.application

import com.langlez.core.OutBoxEventPublisher
import com.langlez.member.application.MemberCommand.Create
import com.langlez.member.application.MemberCommand.Provider
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberProvider
import com.langlez.member.domain.MemberRepository
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MemberService(private val repo: MemberRepository, private val publisher: OutBoxEventPublisher) {

    @Transactional
    @Retryable(maxAttempts = 3, backoff = Backoff(100), retryFor = [Exception::class])
    fun createMember(providerCmd: Provider, command: Create): Member {
        val provider = MemberProvider(providerCmd.id, providerCmd.type, providerCmd.username)
        val member = Member(command.email, command.username, command.nickname, provider)

        member.login()
        val saved = repo.save(member)

        val event = MemberEvent.Created(saved.id, saved.email, saved.username, saved.nickname)
        publisher.publish("MEMBER", saved.id.toString(), "member-created", event)

        return saved
    }

}

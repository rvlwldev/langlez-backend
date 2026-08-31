package com.langlez.member.application

import com.langlez.member.contract.MemberCreatedEvent
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberCreator(
    private val repo: MemberRepository,
    private val tracker: OnlineTracker,
    private val publisher: ApplicationEventPublisher,
) {

    @Transactional
    fun create(
        providerType: Member.Provider,
        providerId: String,
        email: String,
        providerUsername: String,
    ): Member {
        val member = Member(
            email = email,
            handle = Member.randomHandle(),
            provider = providerType,
            providerId = providerId,
            providerDisplayName = providerUsername
        ).apply { updateAccessedAt() }

        val saved = repo.save(member)
            .apply { publisher.publishEvent(MemberCreatedEvent(id, email, handle)) }
            .apply { runCatching { tracker.toOnline(id) } }

        return saved
    }
}

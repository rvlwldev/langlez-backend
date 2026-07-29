package com.langlez.member.application

import com.langlez.core.event.member.MemberCreatedEvent
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.domain.MemberProvider
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberCreator(
    private val repo: MemberRepository,
    private val tracker: MemberOnlineTracker,
    private val publisher: ApplicationEventPublisher,
) {

    @Transactional
    fun create(
        providerType: MemberProvider,
        providerId: String,
        email: String,
        providerUsername: String,
        nickname: String,
    ): Member {
        val member = Member(
            email = email,
            username = Member.generateRandomUsername(),
            nickname = nickname.take(20),
            provider = providerType,
            providerId = providerId,
            providerDisplayName = providerUsername
        ).apply { updateAccessedAt() }

        val saved = repo.save(member)
            .apply { publisher.publishEvent(MemberCreatedEvent(id, email, username, nickname)) }
            .apply { runCatching { tracker.toOnline(username) } }

        return saved
    }
}

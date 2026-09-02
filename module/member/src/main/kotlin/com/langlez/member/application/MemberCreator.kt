package com.langlez.member.application

import com.langlez.member.contract.MemberCreatedEvent
import com.langlez.member.contract.OnlineTracker
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate

@Component
class MemberCreator(
    private val repo: MemberRepository,
    private val tracker: OnlineTracker,
    private val publisher: ApplicationEventPublisher,
    private val tx: TransactionTemplate,
) {

    /**
     * 가입.
     *
     * `tracker.toOnline` 은 `member-api` 포트다(레디스 왕복 3회, 나중에 원격이 된다).
     * 트랜잭션 안에서 부르면 DB 커넥션을 쥔 채 그 왕복을 기다리므로 커밋 뒤로 뺀다.
     *
     * 반대로 `publishEvent` 는 반드시 트랜잭션 안이어야 한다 —
     * `@TransactionalEventListener(BEFORE_COMMIT)` 가 같은 트랜잭션에 아웃박스 행을 넣는다.
     */
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

        val saved = tx.execute {
            repo.save(member)
                .apply { publisher.publishEvent(MemberCreatedEvent(id, email, handle)) }
        }!!

        // 온라인 표시 실패로 가입을 실패시키지 않는다.
        runCatching { tracker.toOnline(saved.id) }

        return saved
    }
}

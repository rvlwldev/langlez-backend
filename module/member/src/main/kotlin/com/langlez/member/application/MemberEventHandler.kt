package com.langlez.member.application

import com.langlez.member.outbox.MemberOutBoxRepository
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MemberEventHandler(private val outboxRepo: MemberOutBoxRepository) {

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: MemberEvent.Created) {
        outboxRepo.save("MEMBER", event.id.toString(), "member-created", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: MemberEvent.UsernameChanged) {
        outboxRepo.save("MEMBER", event.id.toString(), "member-username-changed", event)
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    fun handle(event: MemberEvent.NicknameChanged) {
        outboxRepo.save("MEMBER", event.id.toString(), "member-nickname-changed", event)
    }
}

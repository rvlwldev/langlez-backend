package com.langlez.member.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.event.member.MemberCreatedEvent
import com.langlez.core.event.member.MemberHandleChangedEvent
import com.langlez.core.event.member.MemberWithdrawnEvent
import com.langlez.member.infrastructure.jpa.MemberOutBoxRepository
import com.langlez.member.infrastructure.outbox.MemberOutBox
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MemberEventListener(private val repo: MemberOutBoxRepository, private val mapper: ObjectMapper) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onMemberCreated(event: MemberCreatedEvent) {
        repo.save(MemberOutBox("MEMBER", "member-created", mapper.writeValueAsString(event), event.id.toString()))
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onHandleChanged(event: MemberHandleChangedEvent) {
        repo.save(
            MemberOutBox(
                "MEMBER",
                "member-handle-changed",
                mapper.writeValueAsString(event),
                event.id.toString()
            )
        )
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onMemberWithdrawn(event: MemberWithdrawnEvent) {
        repo.save(MemberOutBox("MEMBER", "member-withdrawn", mapper.writeValueAsString(event), event.id.toString()))
    }
}
package com.langlez.member.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.event.member.MemberCreatedEvent
import com.langlez.core.event.member.MemberNicknameChangedEvent
import com.langlez.core.event.member.MemberUsernameChangedEvent
import com.langlez.member.application.MemberOnlineTracker
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
    fun onUsernameChanged(event: MemberUsernameChangedEvent) {
        repo.save(
            MemberOutBox(
                "MEMBER",
                "member-username-changed",
                mapper.writeValueAsString(event),
                event.id.toString()
            )
        )
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onNicknameChanged(event: MemberNicknameChangedEvent) {
        repo.save(
            MemberOutBox(
                "MEMBER",
                "member-nickname-changed",
                mapper.writeValueAsString(event),
                event.id.toString()
            )
        )
    }

}
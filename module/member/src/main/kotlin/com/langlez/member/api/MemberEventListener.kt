package com.langlez.member.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.core.event.member.MemberCreatedEvent
import com.langlez.core.event.member.MemberNicknameChangedEvent
import com.langlez.core.event.member.MemberUsernameChangedEvent
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.infrastructure.MemberOutBoxRepositoryImpl
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionPhase.AFTER_COMMIT
import org.springframework.transaction.event.TransactionPhase.BEFORE_COMMIT
import org.springframework.transaction.event.TransactionalEventListener

@Component
class MemberEventListener(
    private val repo: MemberOutBoxRepositoryImpl,
    private val tracker: MemberOnlineTracker,
    private val mapper: ObjectMapper,
) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onMemberCreated(event: MemberCreatedEvent) {
        repo.save(
            "MEMBER",
            "member-created",
            mapper.writeValueAsString(event),
            event.id,
        )
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onUsernameChanged(event: MemberUsernameChangedEvent) {
        repo.save(
            "MEMBER",
            "member-username-changed",
            mapper.writeValueAsString(event),
            event.id,
        )
    }

//    @TransactionalEventListener(phase = AFTER_COMMIT)
//    fun onUsernameChangedAndOffline(event: MemberUsernameChangedEvent) = runCatching {
//        cacheManager.getCache("member-username")?.evict(event.oldUsername)
//    }


    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun onNicknameChanged(event: MemberNicknameChangedEvent) {
        repo.save(
            "MEMBER",
            "member-nickname-changed",
            mapper.writeValueAsString(event),
            event.id,
        )
    }
}
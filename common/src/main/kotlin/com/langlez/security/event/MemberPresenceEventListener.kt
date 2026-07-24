package com.langlez.security.event

import com.langlez.core.MemberPresenceTracker
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component

@Component
class MemberPresenceEventListener(
    private val memberPresenceTracker: MemberPresenceTracker
) {

    @Async
    @EventListener
    fun handleMemberAuthenticated(event: MemberAuthenticatedEvent) {
        memberPresenceTracker.markOnline(event.memberId)
    }
}

package com.langlez.member.api

import com.langlez.core.MessageConsumer
import com.langlez.member.application.MemberOnlineTracker
import org.springframework.stereotype.Component

@Component
class MemberConsumer(private val tracker: MemberOnlineTracker) {

    @MessageConsumer(topics = ["member-activity"], group = "member")
    fun onMemberActivity(username: String) = tracker.toOnline(username)

}
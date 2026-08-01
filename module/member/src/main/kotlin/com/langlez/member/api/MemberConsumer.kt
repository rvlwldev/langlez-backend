package com.langlez.member.api

import com.langlez.member.application.MemberOnlineTracker
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class MemberConsumer(private val tracker: MemberOnlineTracker) {

    @KafkaListener(topics = ["member-activity"], groupId = "member")
    fun onMemberActivity(username: String) = tracker.toOnline(username)

}
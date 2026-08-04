package com.langlez.member.api

import com.langlez.core.OnlineTracker
import com.langlez.member.domain.MemberRepository
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

@Component
class MemberConsumer(private val tracker: OnlineTracker, private val repo: MemberRepository) {

    // 핑 메시지는 handle로 온다(외부 발신 계약). tracker는 id 기반이라 여기서 변환한다.
    // MemberRepositoryImpl이 handle 조회를 캐시하고 있어 매 핑마다 DB를 직접 치진 않는다.
    @KafkaListener(topics = ["member-activity"], groupId = "member")
    fun onMemberActivity(handle: String) {
        repo.find(handle)?.let { tracker.toOnline(it.id) }
    }
}
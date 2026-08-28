package com.langlez.chat.infrastructure.outbox

import com.langlez.chat.infrastructure.jpa.ChatOutBoxRepository
import com.langlez.rdb.outbox.OutBoxHistoryProcessor
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 처리 끝난 아웃박스 행을 이력 테이블로 옮긴다.
 *
 * `ChatOutBoxHistory` 엔티티만 있고 채우는 쪽이 없어서 `send()` 가 complete() 후 삭제하지 않는
 * 완료 행이 `chat_event_outbox` 에 계속 쌓이고 있었다.
 */
@Component
internal class ChatOutBoxHistoryScheduler(repo: ChatOutBoxRepository) :
    OutBoxHistoryProcessor<ChatOutBox, ChatOutBoxHistory>(repo) {

    override val chunk = 1000

    override fun toHistory(outbox: ChatOutBox) = ChatOutBoxHistory(outbox)

    @Scheduled(cron = "0 0 6 * * *") // 매일 아침 6시
    @DistributedLock(prefix = "lock:chat-outbox-history")
    override fun archive() = super.archive()
}

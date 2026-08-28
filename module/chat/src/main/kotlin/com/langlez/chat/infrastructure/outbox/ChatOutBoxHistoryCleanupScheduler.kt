package com.langlez.chat.infrastructure.outbox

import com.langlez.chat.infrastructure.jpa.ChatOutBoxHistoryRepository
import com.langlez.rdb.outbox.OutBoxHistoryCleaner
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * 보존 기간(90일)이 지난 이력 행을 지운다. 안 지우면 `chat_event_outbox_history` 가 무한 증가한다.
 *
 * 이관 스케줄러(6시) 바로 뒤가 아니라 30분 띄운 것은, 같은 순간 실행돼도 방금 옮겨진 행은
 * 보존 기간에 한참 못 미쳐 지워지지 않으니 실질적 차이는 없지만 관측 로그가 겹치지 않게 하기 위함이다.
 */
@Component
internal class ChatOutBoxHistoryCleanupScheduler(repo: ChatOutBoxHistoryRepository) :
    OutBoxHistoryCleaner<ChatOutBoxHistory>(repo) {

    @Scheduled(cron = "0 30 6 * * *") // 매일 아침 6시 30분
    @DistributedLock(prefix = "lock:chat-outbox-history-cleanup")
    override fun clean() = super.clean()
}

package com.langlez.chat.application

import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.ChatRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.Duration
import java.time.Instant

/**
 * 이중 쓰기 대사(reconciliation).
 *
 * 전송은 Mongo(메시지) → Postgres(방 프리뷰·안 읽은 수) 순서다. 두 저장소를 가로지르는 원자성이 없어서
 * 그 사이에 프로세스가 죽으면 메시지는 남고 방 메타만 낡은 상태로 굳는다. 이 창은 저장소를 둘로 나눈
 * 이상 원리적으로 없앨 수 없고, 주기적으로 메우는 수밖에 없다.
 *
 * **모든 계산은 멱등하다.** 프리뷰는 마지막 메시지로 덮어쓰고, 안 읽은 수는 더하는 게 아니라 다시 세어
 * 설정한다. 같은 방을 몇 번 돌려도 결과가 같아야 한다 — 스케줄러는 락 만료·재배포로 겹쳐 돌 수 있다.
 */
@Component
internal class ChatReconciler(
    private val repo: ChatRepository,
    private val messages: ChatMessageRepository,
    private val tx: TransactionTemplate,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 인스턴스가 여러 대여도 한 대만 돈다. 겹쳐 돌아도 결과는 같지만 같은 방을 두 번 훑을 이유가 없다.
     * 락을 못 잡으면 다른 인스턴스가 이미 돌고 있다는 뜻이라 조용히 건너뛴다.
     */
    @Scheduled(cron = "0 */5 * * * *")
    @DistributedLock(prefix = "lock:chat-reconcile", throwOnFailure = false)
    fun reconcile() {
        val since = Instant.now().minus(WINDOW)

        messages.findRoomIdsSince(since).forEach { roomId ->
            // 방 하나가 실패해도 나머지는 맞춰야 한다. 여기서 멈추면 뒤쪽 방은 다음 주기까지 낡은 채 남는다.
            runCatching { reconcileRoom(roomId) }
                .onFailure { log.warn("채팅방 대사 실패. roomId=$roomId", it) }
        }
    }

    /**
     * 방 하나를 맞춘다.
     *
     * 대부분의 방은 이미 일관돼 조회 두 번으로 끝난다. 어긋난 방만 쓰기 트랜잭션을 연다 —
     * 5분마다 활성 방 전부에 빈 트랜잭션을 여는 건 커넥션 낭비다.
     */
    private fun reconcileRoom(roomId: Long) {
        val latest = messages.findByRoom(roomId, 1, null).firstOrNull() ?: return

        // 이미 반영된 방은 손대지 않는다. 두 번째 실행이 아무 일도 하지 않는 것도 여기서 보장된다.
        if (repo.findRoom(roomId)?.isBehind(latest.createdAt) != true) return

        log.info("채팅방 메타가 메시지보다 뒤처져 있어 다시 맞춘다. roomId=$roomId, seq=${latest.seq}")

        tx.executeWithoutResult {
            // 트랜잭션 안에서 다시 읽어야 영속 상태라 더티 체킹으로 반영된다.
            repo.findRoom(roomId)?.onMessage(latest.preview(), latest.createdAt)

            // 증가(increaseUnread)가 아니라 다시 세어 설정한다. 얼마나 밀렸는지 알 수 없을뿐더러,
            // 더하는 방식이면 대사가 두 번 돌 때 같은 메시지를 두 번 세게 된다.
            repo.findParticipants(roomId).forEach { participant ->
                participant.apply {
                    syncUnread(messages.countUnread(roomId, memberId, lastReadAt))

                    // 재입장 정책: 나간 뒤 새 메시지가 오면 방이 되살아난다.
                    // 전송 때 이 갱신이 누락되면 그 사람 목록에서 방이 계속 안 보여
                    // 프리뷰만 고쳐봐야 메시지가 통째로 묻힌다.
                    if (hasLeft() && leftAt!! < latest.createdAt) rejoin()
                }.also(repo::saveParticipant)
            }
        }
    }

    companion object {
        /**
         * 되짚어 볼 창. 짧으면 장애가 길어졌을 때 못 메우고, 길면 매 주기 훑는 양만 는다.
         * 5분마다 도니 30분이면 같은 방을 여섯 번까지 다시 볼 기회가 있다.
         */
        private val WINDOW: Duration = Duration.ofMinutes(30)
    }
}

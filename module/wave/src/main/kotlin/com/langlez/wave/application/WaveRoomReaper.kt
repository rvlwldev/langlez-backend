package com.langlez.wave.application

import com.langlez.redis.distributedLock.DistributedLock
import com.langlez.wave.domain.WaveRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/**
 * 버려진 방 정리.
 *
 * 방을 닫는 경로는 명시적 퇴장과 방장 종료뿐이었다. 둘 다 클라이언트가 살아 있어야 도는 경로라,
 * 앱을 강제 종료하거나 인스턴스가 통째로 죽으면 `wave_rooms.ended_at` 이 NULL 로 굳는다.
 * 레디스 참여자 집합은 TTL 이 있어 언젠가 사라지지만 **Postgres 행에는 만료가 없어 영구적이다.**
 * 아무도 없는 방이 목록 앞자리를 계속 차지한다.
 *
 * WebSocket 종료 이벤트([com.langlez.wave.WaveWebSocketConfiguration])가 정상 경로를 덮고,
 * 이 리퍼는 그 이벤트조차 못 오는 경우 — 인스턴스 강제 종료, REST 로 입장만 하고 소켓을 안 연 경우,
 * 이벤트 처리 자체가 실패한 경우 — 를 받는다. 둘 중 하나만으로는 구멍이 남는다.
 *
 * [WaveService] 와 별도 빈이다. `@DistributedLock` 은 Spring AOP 프록시로 도는데
 * 같은 클래스 안에서 부르면 advice 를 타지 않아 락이 조용히 안 걸린다.
 */
@Component
internal class WaveRoomReaper(
    private val repo: WaveRepository,
    private val service: WaveService,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "0 */10 * * * *")
    @DistributedLock(prefix = "lock:wave-room-reap")
    fun reapAbandoned() = reapAbandonedStartedBefore(Instant.now().minus(GRACE))

    /**
     * 기준 시각을 인자로 받는다. `@Scheduled` 는 인자 있는 메서드에 못 붙어 진입점을 따로 둔다.
     * 테스트가 경계 시각을 고정할 수 있어야 "방금 만든 방은 안 건드린다"를 검증할 수 있다.
     *
     * **[GRACE] 가 필요한 이유**: `createRoom` 은 방을 저장한 뒤에야 개설자를 참여자로 넣는다.
     * 그 찰나에 리퍼가 끼어들면 참여자 0 명으로 보여 갓 만든 방을 닫아 버린다. 개설자는 방 정보를
     * 응답으로 받고도 이후 요청이 전부 409 다.
     *
     * **한 주기에 [CHUNK] 건까지만 처리한다.** 남으면 10분 뒤가 마저 가져간다. 다 비울 때까지
     * 도는 루프를 두면, 아래처럼 실패를 삼키는 구조에서 같은 행이 계속 걸려 무한 루프가 된다.
     */
    fun reapAbandonedStartedBefore(startedBefore: Instant) {
        repo.findOpenStartedBefore(startedBefore, CHUNK).forEach { room ->
            // 방 하나에서 난 실패가 나머지를 막으면 안 된다. 다음 주기가 다시 잡는다.
            runCatching { service.closeIfAbandoned(room.id) }
                .onFailure { logger.warn("버려진 방 정리 실패. room={}", room.id, it) }
        }
    }

    private companion object {
        val GRACE: Duration = Duration.ofMinutes(5)

        const val CHUNK = 500
    }
}

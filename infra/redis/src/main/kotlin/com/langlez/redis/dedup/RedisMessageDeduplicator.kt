package com.langlez.redis.dedup

import com.langlez.core.MessageDeduplicator
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration

/**
 * SETNX + TTL 기반 중복 검사.
 *
 * 키는 `토픽 + 페이로드` 의 SHA-256 이다. 페이로드 전문을 키로 쓰면 채팅 본문이 레디스에 그대로 남고
 * 길이도 들쭉날쭉해서 해시로 고정한다. 토픽을 앞에 붙이는 건 같은 페이로드가 다른 토픽으로 흐를 때
 * 서로를 막지 않게 하려는 것이다.
 *
 * 코덱을 `StringCodec` 으로 못 박는다. Redisson 기본 코덱은 바이너리 직렬화라 키·값이
 * 원시 바이트로 들어가고, redis-cli 로 들여다볼 수 없어 운영 중 확인이 안 된다.
 */
@Component
class RedisMessageDeduplicator(private val redisson: RedissonClient) : MessageDeduplicator {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 레디스가 죽으면 통과시킨다(fail-open). 여기서 막으면 장애 시간 동안의 알림이
    // 통째로 사라진다 — 핸들러가 예외 없이 끝나 오프셋이 커밋되므로 되살릴 방법이 없다.
    // 반대로 통과의 최악은 알림이 두 번 뜨는 것, 즉 이 장치가 없던 상태와 같다.
    // 인가·차단 같은 보안 판정이었다면 fail-open 이 곧 우회라 반대로 잡아야 하지만,
    // 이건 사용자 경험 보호라 module/CLAUDE.md 의 "삼켜도 되는 실패" 에 해당한다.
    override fun isDuplicate(topic: String, payload: String): Boolean = runCatching {
        !bucket(topic, payload).setIfAbsent(MARK, TTL)
    }.getOrElse {
        logger.warn("메시지 중복 검사 실패, 중복 위험을 안고 통과시킨다: topic={}", topic, it)
        false
    }

    // 해제에 실패해도 TTL 이 지나면 어차피 풀린다. 그 사이 재시도가 걸러지는 건 감수한다 —
    // 여기서 예외를 올리면 원래 실패 원인을 덮어써 무엇 때문에 실패했는지가 사라진다.
    override fun release(topic: String, payload: String) {
        runCatching { bucket(topic, payload).delete() }
            .onFailure { logger.warn("메시지 중복 표시 해제 실패: topic={}", topic, it) }
    }

    private fun bucket(topic: String, payload: String) =
        redisson.getBucket<String>("dedup:$topic:${sha256(topic + payload)}", StringCodec.INSTANCE)

    private fun sha256(input: String): String =
        MessageDigest.getInstance("SHA-256").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val MARK = "1"

        /**
         * 재배달이 실제로 몰리는 창을 덮되 그 이상 들고 있지 않는다.
         *
         * 가장 긴 재배달 경로는 `KafkaConfiguration` 의 컨슈머 재시도(`maxElapsedTime = 5분`)와
         * 리밸런싱(`max.poll.interval.ms` 기본 5분)이다. 1시간이면 그 12배라
         * 배포 중 재시작이나 짧은 장애 복구까지 덮는다.
         *
         * 더 늘려도 정상 이벤트를 막지는 않는다 — 키에 followId/messageId 가 섞여 있어
         * 같은 값이 두 번 나오지 않는다. 순전히 레디스 메모리 문제라 1시간에서 끊는다.
         * (컨슈머 그룹 오프셋을 처음부터 되감는 운영 리플레이는 어떤 TTL 로도 못 덮고, 덮어서도 안 된다.)
         */
        val TTL: Duration = Duration.ofHours(1)
    }
}

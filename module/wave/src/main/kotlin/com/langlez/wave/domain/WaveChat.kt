package com.langlez.wave.domain

import java.time.Instant

/**
 * 사라지는 채팅 한 줄.
 *
 * 엔티티가 아니다 — 저장소가 레디스 링버퍼뿐이라 PK 도 시퀀스도 없다.
 * 패키지가 `com.langlez.` 아래여야 한다. 레디스 코덱이 이 접두사에만 타입 정보를 남긴다
 * (RedissonConfiguration.redisCodec 참고).
 */
data class WaveChat(
    val roomId: Long,
    val senderId: Long,
    val content: String,
    val sentAt: Instant = Instant.now(),
)

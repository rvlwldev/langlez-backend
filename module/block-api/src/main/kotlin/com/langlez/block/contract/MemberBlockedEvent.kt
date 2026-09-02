package com.langlez.block.contract

/**
 * 차단 도메인 이벤트. `member-blocked` 토픽으로 나간다.
 *
 * 차단은 팔로우 관계를 양방향으로 끊어야 하는데 그 두 데이터가 다른 모듈 소유다.
 * block 이 follow 를 직접 부를 수 없으니 이 이벤트로 알린다.
 *
 * **차단의 효력 자체는 즉시고, 팔로우 행 정리만 지연된다.** 그 창의 노출 범위는
 * `BlockService.block` KDoc 에 적어 두었다.
 *
 * [occurredAt] 은 차단 요청 시각(epoch millis)이다. **컨슈머 멱등성의 식별자라 빼면 안 된다.**
 * `MessageDeduplicator` 는 페이로드 해시로 재배달을 가리는데, (blockerId, blockedId) 만으로는
 * 이미 차단한 상대를 다시 차단하는 **수습 경로**가 첫 차단과 완전히 같은 페이로드가 되어
 * 통째로 걸러진다 — 과거에 반쪽만 끊긴 팔로우 행을 지우려고 만든 경로가 죽는다.
 * 요청마다 값이 달라지는 필드가 하나는 있어야 그 둘이 갈린다.
 *
 * `Instant` 가 아니라 `Long` 인 이유: 계약 모듈은 JDK 타입만 쓴다는 규칙과 별개로,
 * `Instant` 는 소비 쪽 `ObjectMapper` 에 JavaTimeModule 이 등록돼 있어야 풀린다.
 * 페이로드가 한 군데서라도 안 풀리면 그 이벤트는 DLT 로 간다.
 */
data class MemberBlockedEvent(
    val blockerId: Long,
    val blockedId: Long,
    val occurredAt: Long,
)

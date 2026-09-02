package com.langlez.relationship.contract

/**
 * 차단 여부 조회. relationship 모듈이 구현한다.
 *
 * 채팅·매칭 등이 차단을 존중해야 하는데, 그 모듈들이 relationship 의 저장소를
 * 직접 들여다보면 경계가 무너진다. 조회만 포트로 뽑는다.
 */
interface BlockQuery {
    /** 둘 중 어느 방향으로든 차단이 있으면 true */
    fun isBlockedBetween(memberId: Long, otherId: Long): Boolean

    /**
     * 목록 화면용. [candidateIds] 중 [viewerId] 와 어느 방향으로든 차단이 있는 id 만 돌려준다.
     *
     * 목록 항목 수만큼 [isBlockedBetween] 을 돌면 이 포트가 네트워크가 될 때 왕복이 그만큼 늘어난다.
     * 판정 규칙(양방향)은 [isBlockedBetween] 과 반드시 같아야 한다 —
     * 갈라지면 한쪽 경로에서만 차단이 먹는 구멍이 생긴다.
     */
    fun blockedAmong(viewerId: Long, candidateIds: Collection<Long>): Set<Long>
}

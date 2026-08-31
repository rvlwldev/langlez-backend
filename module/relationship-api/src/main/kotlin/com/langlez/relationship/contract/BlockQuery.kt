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
}

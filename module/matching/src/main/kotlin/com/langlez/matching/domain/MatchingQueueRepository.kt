package com.langlez.matching.domain

import java.time.Instant

/**
 * Redis Sorted Set("matching:queue") 기반 실시간 매칭 대기열과,
 * 대기 시작 시각(matching:joined-at:{memberId})을 다루는 저장소.
 */
interface MatchingQueueRepository {

    fun isQueued(memberId: Long): Boolean

    /** ZSET에 memberId를 score로 추가한다. 이미 있으면 score를 갱신한다. */
    fun add(memberId: Long, score: Double)

    /** ZSET에서 memberId를 제거한다. 실제로 제거되었으면 true(멱등적 CAS 가드로 사용). */
    fun remove(memberId: Long): Boolean

    fun score(memberId: Long): Double?

    /** score가 [min, max] 범위(양 끝 포함)에 있는 대기자 목록을 반환한다. */
    fun candidatesInRange(min: Double, max: Double): List<Long>

    /** 현재 큐에 있는 모든 대기자 목록. */
    fun allMembers(): List<Long>

    fun saveJoinedAt(memberId: Long, at: Instant)

    fun findJoinedAt(memberId: Long): Instant?

    fun removeJoinedAt(memberId: Long)

    fun saveFilter(memberId: Long, filter: MatchingQueueFilter)

    fun findFilter(memberId: Long): MatchingQueueFilter?

    fun removeFilter(memberId: Long)
}


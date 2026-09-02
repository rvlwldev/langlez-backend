package com.langlez.notification.domain

interface NotificationMuteRepository {
    /** 끈 유형이 없으면 빈 집합(전부 켠 상태). */
    fun find(memberId: Long): Set<String>

    /** 다건 배치 조회. 끈 유형이 없는 회원은 결과 맵에서 빠진다. */
    fun findAll(memberIds: Collection<Long>): Map<Long, Set<String>>

    /** 전체 교체. 빈 집합을 주면 그 회원의 mute 행을 전부 지운다(전부 켠 상태로). */
    fun replaceAll(memberId: Long, types: Set<String>)
}

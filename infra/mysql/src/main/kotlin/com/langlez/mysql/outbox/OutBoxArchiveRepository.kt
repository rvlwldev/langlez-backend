package com.langlez.mysql.outbox

import java.time.Instant

/**
 * OutBoxArchiver 스케줄러가 요구하는 저장소 계약.
 * 각 모듈은 자기 엔티티 타입(History, Archive)으로 이 인터페이스를 구현한다.
 */
interface OutBoxArchiveRepository<H : OutBoxHistory, A : OutBoxArchive> {

    /** 지정한 시각(cutoff) 이전의 History 이벤트를 limit만큼 오래된 순으로 조회한다. */
    fun findAllHistoriesBefore(cutoff: Instant, limit: Int): List<H>

    /** 생성된 아카이브 엔티티를 저장한다. */
    fun save(archive: A): A

    /** 이관이 완료된 History 엔티티들을 삭제한다. */
    fun delete(histories: List<H>)
}

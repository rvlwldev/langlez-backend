package com.langlez.rdb.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
import java.time.ZoneId
import org.springframework.transaction.support.TransactionTemplate

/**
 * OutBoxHistory 데이터를 청크 단위(ARCHIVE_CHUNK_SIZE)로 묶어 JSON 문자열로 압축 아카이빙하는 스케줄러 베이스.
 *
 * 하위 모듈 스케줄러 클래스는 `@Component`로 등록하고, 메서드를 오버라이드하여 `@Scheduled` +
 * `@DistributedLock`을 부착한다.
 *
 * ```
 * @Scheduled(cron = "0 0 3 1 * *") // 매월 1일 새벽 3시 실행
 * @DistributedLock(prefix = "lock:echo-outbox-archive", ttl = -1, wait = 0, retries = 0, throwOnFailure = false)
 * override fun archiveHistory(targetDate: LocalDate) = super.archiveHistory(targetDate)
 * ```
 */
abstract class OutBoxArchiver<H : OutBoxHistory, A : OutBoxArchive>(
    protected val repo: OutBoxArchiveRepository<H, A>,
    protected val tx: TransactionTemplate,
    protected val mapper: ObjectMapper,
    private val toArchive: (domain: String, date: LocalDate, index: Int, count: Int, json: String) -> A,
) {

    /**
     * 지정된 일자(targetDate, 기본 2달 전) 이전의 History 레코드를 청크(1,000개) 단위로 JSON으로 묶어 Archive 테이블로 이관한다.
     * 최근 30일~60일간의 데이터는 History 테이블에 유연하게 유지되어 빠른 조회가 가능하다.
     */
    open fun archive(before: LocalDate = LocalDate.now().minusMonths(2)) {
        val cutoff = before.atStartOfDay(ZoneId.systemDefault()).toInstant()
        var index = 0

        do {
            val count = tx.execute {
                val histories = repo.findAllHistoriesBefore(cutoff, ARCHIVE_CHUNK_SIZE)
                if (histories.isEmpty()) return@execute 0

                val domain = histories.first().domain
                val list = mapper.writeValueAsString(histories)
                val archive = toArchive(domain, before, index, histories.size, list)

                repo.save(archive)
                repo.delete(histories)

                index++
                histories.size
            } ?: 0
        } while (count == ARCHIVE_CHUNK_SIZE)
    }

    companion object {
        const val ARCHIVE_CHUNK_SIZE = 10000
    }
}

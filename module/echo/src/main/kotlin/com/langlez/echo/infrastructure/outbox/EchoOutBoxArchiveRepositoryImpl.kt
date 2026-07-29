package com.langlez.echo.infrastructure.outbox

import com.langlez.echo.infrastructure.outbox.jpa.EchoOutBoxArchiveJpaRepository
import com.langlez.echo.infrastructure.outbox.jpa.EchoOutBoxHistoryJpaRepository
import java.time.Instant
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class EchoOutBoxArchiveRepositoryImpl(
    private val historyJpaRepository: EchoOutBoxHistoryJpaRepository,
    private val archiveJpaRepository: EchoOutBoxArchiveJpaRepository,
) : EchoOutBoxArchiveRepository {

    override fun findAllHistoriesBefore(cutoff: Instant, limit: Int): List<EchoOutBoxHistory> {
        return historyJpaRepository.findByCreatedAtBeforeOrderByCreatedAtAsc(cutoff, PageRequest.of(0, limit))
    }

    override fun save(archive: EchoOutBoxArchive): EchoOutBoxArchive {
        return archiveJpaRepository.save(archive)
    }

    override fun delete(histories: List<EchoOutBoxHistory>) {
        historyJpaRepository.deleteAllInBatch(histories)
    }
}

package com.langlez.rdb.outbox

import jakarta.persistence.EntityManager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.transaction.support.TransactionTemplate

abstract class OutBoxHistoryProcessor<T : OutBox, H : OutBoxHistory>(private val repo: OutBoxRepository<T>) {

    @Autowired
    private lateinit var tx: TransactionTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    open val chunk = 1000

    protected abstract fun toHistory(outbox: T): H

    open fun archive() {
        var count: Int

        do {
            count = tx.execute {
                val completes = repo.fetchProcessed(chunk)
                if (completes.isEmpty()) return@execute 0

                completes.forEach { entityManager.persist(toHistory(it)) }
                repo.deleteAll(completes)

                return@execute completes.size
            } ?: 0
        } while (count == chunk)
    }
}
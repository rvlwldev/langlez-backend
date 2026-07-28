package com.langlez.mysql.outbox

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Instant
import java.time.LocalDate
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

private class TestOutBoxArchive(
    domain: String,
    date: LocalDate,
    index: Int,
    count: Int,
    data: String,
) : OutBoxArchive(domain, date, index, count, data)

private class TestOutBoxHistoryItem(
    id: Long,
    domain: String,
    topic: String,
    payload: String?,
    key: String?,
    attempts: Int,
    status: OutBoxStatus,
    createdAt: Instant,
) : OutBoxHistory(id, domain, topic, payload, key, attempts, status, createdAt)

private class TestOutBoxArchiver(
    repo: OutBoxArchiveRepository<TestOutBoxHistoryItem, TestOutBoxArchive>,
    tx: TransactionTemplate,
    mapper: ObjectMapper,
) : OutBoxArchiver<TestOutBoxHistoryItem, TestOutBoxArchive>(
    repo = repo,
    tx = tx,
    mapper = mapper,
    toArchive = ::TestOutBoxArchive,
)

class OutBoxArchiverTest : BehaviorSpec({

    val repo = mockk<OutBoxArchiveRepository<TestOutBoxHistoryItem, TestOutBoxArchive>>()
    val tx = mockk<TransactionTemplate>()
    val mapper = jacksonObjectMapper().apply {
        registerModule(JavaTimeModule())
    }

    every { tx.execute<Int>(any()) } answers {
        val callback = firstArg<TransactionCallback<Int>>()
        callback.doInTransaction(mockk(relaxed = true))
    }
    every { repo.save(any()) } answers { firstArg() }
    every { repo.delete(any()) } returns Unit

    val archiver = TestOutBoxArchiver(repo, tx, mapper)

    Given("2달 이전의 OutBoxHistory 레코드가 존재할 때") {
        val history1 = TestOutBoxHistoryItem(1L, "ECHO", "topic-1", "payload1", "key1", 1, OutBoxStatus.COMPLETE, Instant.now())
        val history2 = TestOutBoxHistoryItem(2L, "ECHO", "topic-2", "payload2", "key2", 1, OutBoxStatus.COMPLETE, Instant.now())

        every { repo.findAllHistoriesBefore(any(), any()) } returnsMany listOf(
            listOf(history1, history2),
            emptyList()
        )

        When("archive() 실행 시") {
            archiver.archive(LocalDate.now().minusMonths(2))

            Then("히스토리 2건이 1개의 JSON 배열 로우로 압축 저장되고 원본은 삭제된다") {
                verify(exactly = 1) { repo.save(any()) }
                verify(exactly = 1) { repo.delete(listOf(history1, history2)) }
            }
        }
    }
})

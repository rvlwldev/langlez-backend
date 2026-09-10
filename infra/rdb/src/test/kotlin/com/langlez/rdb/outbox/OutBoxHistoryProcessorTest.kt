package com.langlez.rdb.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

class TestHistoryProcessor(
    repo: OutBoxRepository<TestOutBox>,
    tx: TransactionTemplate,
    entityManager: EntityManager,
) : OutBoxHistoryProcessor<TestOutBox, TestOutBoxHistory>(repo) {

    override val chunk = 2

    override fun toHistory(outbox: TestOutBox): TestOutBoxHistory = TestOutBoxHistory(outbox)

    init {
        val txField = OutBoxHistoryProcessor::class.java.getDeclaredField("tx")
        txField.isAccessible = true
        txField.set(this, tx)

        val emField = OutBoxHistoryProcessor::class.java.getDeclaredField("entityManager")
        emField.isAccessible = true
        emField.set(this, entityManager)
    }
}

private fun noTxTransactionTemplate(): TransactionTemplate {
    val tx = mockk<TransactionTemplate>()
    every { tx.execute(any<TransactionCallback<Int>>()) } answers {
        firstArg<TransactionCallback<Int>>().doInTransaction(mockk(relaxed = true))
    }
    return tx
}

class OutBoxHistoryProcessorTest : BehaviorSpec({

    Given("실패(FAILED)한 아웃박스 엔티티가 있을 때") {
        val failedOutbox = TestOutBox(key = "fail-1").apply {
            dispatch()
            fail(maxRetries = 1)
        }

        When("OutBoxHistory 로 변환하면") {
            val history = TestOutBoxHistory(failedOutbox)

            Then("failedAt 시각이 누락 없이 보존된다") {
                failedOutbox.failedAt.shouldNotBeNull()
                history.failedAt shouldBe failedOutbox.failedAt
                history.status shouldBe OutBox.Status.FAILED
                history.completedAt.shouldBeNull()
                history.tries shouldBe 1
                history.key shouldBe "fail-1"
            }
        }
    }

    Given("완료(COMPLETE)된 아웃박스 엔티티가 있을 때") {
        val completeOutbox = TestOutBox(key = "ok-1").apply {
            dispatch()
            complete()
        }

        When("OutBoxHistory 로 변환하면") {
            val history = TestOutBoxHistory(completeOutbox)

            Then("completedAt 은 보존되고 failedAt 은 null 이다") {
                completeOutbox.completedAt.shouldNotBeNull()
                history.completedAt shouldBe completeOutbox.completedAt
                history.failedAt.shouldBeNull()
                history.status shouldBe OutBox.Status.COMPLETE
            }
        }
    }

    Given("FAILED 및 COMPLETE 아웃박스 이벤트가 아카이빙 대상일 때") {
        val repo = mockk<OutBoxRepository<TestOutBox>>(relaxed = true)
        val entityManager = mockk<EntityManager>(relaxed = true)
        val tx = noTxTransactionTemplate()

        val failedOutbox = TestOutBox(key = "fail-1").apply {
            dispatch()
            fail(maxRetries = 1)
        }
        val completeOutbox = TestOutBox(key = "ok-1").apply {
            dispatch()
            complete()
        }

        val processed = listOf(failedOutbox, completeOutbox)
        every { repo.fetchProcessed(2) } returns processed andThen emptyList()

        val persisted = mutableListOf<TestOutBoxHistory>()
        every { entityManager.persist(any()) } answers {
            persisted.add(firstArg())
        }

        val processor = TestHistoryProcessor(repo, tx, entityManager)

        When("archive() 를 실행하면") {
            processor.archive()

            Then("이력 테이블로 옮겨지면서 FAILED 이벤트의 failedAt 이 보존된다") {
                persisted.size shouldBe 2

                val failedHistory = persisted.first { it.status == OutBox.Status.FAILED }
                failedHistory.failedAt.shouldNotBeNull()
                failedHistory.failedAt shouldBe failedOutbox.failedAt
                failedHistory.completedAt.shouldBeNull()

                val completeHistory = persisted.first { it.status == OutBox.Status.COMPLETE }
                completeHistory.completedAt.shouldNotBeNull()
                completeHistory.completedAt shouldBe completeOutbox.completedAt
                completeHistory.failedAt.shouldBeNull()

                verify(exactly = 1) { repo.deleteAllInBatch(processed) }
            }
        }
    }
})

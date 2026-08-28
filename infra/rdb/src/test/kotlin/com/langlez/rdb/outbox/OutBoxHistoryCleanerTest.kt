package com.langlez.rdb.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.springframework.data.domain.Pageable
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

@Entity
@Table(name = "test_outbox_history")
class TestOutBoxHistory(outbox: OutBox) : OutBoxHistory(outbox)

/** `tx` 를 실행하는 `TransactionTemplate` 을 실제 트랜잭션 없이 콜백만 실행하는 대역으로 바꿔 단위 테스트한다. */
class TestCleaner(
    repo: OutBoxHistoryRepository<TestOutBoxHistory>,
    tx: TransactionTemplate,
) : OutBoxHistoryCleaner<TestOutBoxHistory>(repo) {

    override val chunk = 2
    override val retentionDays = 30L

    init {
        val field = OutBoxHistoryCleaner::class.java.getDeclaredField("tx")
        field.isAccessible = true
        field.set(this, tx)
    }
}

private fun noTxTransactionTemplate(): TransactionTemplate {
    val tx = mockk<TransactionTemplate>()
    every { tx.execute(any<TransactionCallback<Int>>()) } answers {
        firstArg<TransactionCallback<Int>>().doInTransaction(mockk(relaxed = true))
    }
    return tx
}

class OutBoxHistoryCleanerTest : BehaviorSpec({

    Given("보존 기간이 지난 이력 행이 청크 크기(2)보다 적게 있을 때") {
        val repo = mockk<OutBoxHistoryRepository<TestOutBoxHistory>>(relaxed = true)
        val cutoff = slot<Instant>()
        val row = TestOutBoxHistory(TestOutBox())

        every { repo.findAllByCreatedAtBefore(capture(cutoff), any<Pageable>()) } returns listOf(row) andThen emptyList()

        val cleaner = TestCleaner(repo, noTxTransactionTemplate())

        When("clean() 을 실행하면") {
            cleaner.clean()

            Then("보존 기간(30일) 이전을 기준 시각으로 조회한다") {
                val expected = Instant.now().minusSeconds(30 * 86400)
                // 실행 시점 오차를 감안해 1초 이내인지만 본다
                Math.abs(cutoff.captured.epochSecond - expected.epochSecond) shouldBeLessThanOrEqual 1L
            }

            Then("조회된 행을 지우고, 한 번만 조회한 뒤 멈춘다") {
                verify(exactly = 1) { repo.deleteAllInBatch(listOf(row)) }
                verify(exactly = 1) { repo.findAllByCreatedAtBefore(any(), any()) }
            }
        }
    }

    Given("보존 기간이 지난 행이 청크 크기(2)만큼 꽉 찬 페이지가 두 번 나올 때") {
        val repo = mockk<OutBoxHistoryRepository<TestOutBoxHistory>>(relaxed = true)
        val full = listOf(TestOutBoxHistory(TestOutBox()), TestOutBoxHistory(TestOutBox()))

        every { repo.findAllByCreatedAtBefore(any(), any<Pageable>()) } returns full andThen full andThen emptyList()

        val cleaner = TestCleaner(repo, noTxTransactionTemplate())

        When("clean() 을 실행하면") {
            cleaner.clean()

            Then("청크가 꽉 차는 동안 계속 반복해서 지운다") {
                // 딱 chunk(2) 만큼 나오면 더 있을 수 있다고 보고 다음 페이지를 또 가져온다.
                // 빈 페이지가 나와야 멈춘다 — 안 그러면 대량 삭제가 첫 청크에서 조용히 끝난다.
                verify(exactly = 3) { repo.findAllByCreatedAtBefore(any(), any()) }
                verify(exactly = 2) { repo.deleteAllInBatch(full) }
            }
        }
    }
})

package com.langlez.mysql.outbox

import com.langlez.core.MessageQueue
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

private class TestOutBox(aggregateType: String, aggregateId: String, eventName: String, payload: String) :
    AbstractOutBox(aggregateType, aggregateId, eventName, payload)

private class TestOutBoxHistory(outbox: TestOutBox) : AbstractOutBoxHistory(
    id = outbox.id,
    aggregateType = outbox.aggregateType,
    aggregateId = outbox.aggregateId,
    eventName = outbox.eventName,
    payload = outbox.payload,
    attempts = outbox.attempts,
    createdAt = outbox.createdAt,
)

private class TestOutBoxScheduler(
    repo: OutBoxRepository<TestOutBox, TestOutBoxHistory>,
    messageQueue: MessageQueue,
    transaction: TransactionTemplate,
) : AbstractOutBoxScheduler<TestOutBox, TestOutBoxHistory>(repo, messageQueue, transaction) {
    override fun toHistory(outbox: TestOutBox): TestOutBoxHistory = TestOutBoxHistory(outbox)
}

/** attempts를 리플렉션으로 강제 조작해 "재시도 소진됐는데 상태는 아직 READY/PROCESSING"인 poison row를 재현한다. */
private fun forceAttempts(outbox: AbstractOutBox, attempts: Int) {
    val field = AbstractOutBox::class.java.getDeclaredField("attempts")
    field.isAccessible = true
    field.set(outbox, attempts)
}

class AbstractOutBoxSchedulerTest : BehaviorSpec({

    val repo = mockk<OutBoxRepository<TestOutBox, TestOutBoxHistory>>()
    val messageQueue = mockk<MessageQueue>()
    val transaction = mockk<TransactionTemplate>()

    every { transaction.execute<Any?>(any()) } answers {
        val callback = firstArg<TransactionCallback<Any?>>()
        callback.doInTransaction(mockk(relaxed = true))
    }
    every { repo.saveAll(any()) } answers { firstArg() }

    val scheduler = TestOutBoxScheduler(repo, messageQueue, transaction)

    Given("배치에 poison row(재시도 소진, 상태는 아직 READY)와 정상 row가 섞여 있으면") {
        val poison = TestOutBox("AGG", "1", "poison-event", "{}")
        forceAttempts(poison, AbstractOutBox.MAX_ATTEMPTS)

        val normal = TestOutBox("AGG", "2", "normal-event", "{}")

        every { repo.findToDispatch(any()) } returns listOf(poison, normal)
        every { messageQueue.publish(any(), any(), any()) } returns Unit

        scheduler.dispatchEvents()

        Then("poison row는 FAILED로 격리되고, 정상 row는 그대로 발행/완료된다") {
            poison.status shouldBe OutBoxStatus.FAILED
            normal.status shouldBe OutBoxStatus.COMPLETE
            verify(exactly = 1) { messageQueue.publish(any(), any(), any()) }
        }
    }
})

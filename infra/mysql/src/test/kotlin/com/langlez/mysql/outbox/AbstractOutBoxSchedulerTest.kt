package com.langlez.mysql.outbox

import com.langlez.core.message.MessageProducer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.transaction.support.TransactionCallback
import org.springframework.transaction.support.TransactionTemplate

private class TestOutBox(domain: String, topic: String, payload: String?, key: String?) :
    OutBox(domain, topic, payload, key)

private class TestOutBoxHistory(
    id: Long,
    domain: String,
    topic: String,
    payload: String?,
    key: String?,
    attempts: Int,
    status: OutBoxStatus,
    createdAt: java.time.Instant,
) : OutBoxHistory(id, domain, topic, payload, key, attempts, status, createdAt) {
    constructor(outbox: TestOutBox) : this(
        id = outbox.id,
        domain = outbox.domain,
        topic = outbox.topic,
        payload = outbox.payload,
        key = outbox.key,
        attempts = outbox.attempts,
        status = outbox.status,
        createdAt = outbox.createdAt,
    )
}

private class TestOutBoxProcessor(
    repo: OutBoxRepository<TestOutBox, TestOutBoxHistory>,
    messageProducer: MessageProducer,
    transaction: TransactionTemplate,
) : OutBoxProcessor<TestOutBox, TestOutBoxHistory>(
    repo = repo,
    producer = messageProducer,
    tx = transaction,
    toHistory = ::TestOutBoxHistory,
)

/** attempts를 리플렉션으로 강제 조작해 "재시도 소진됐는데 상태는 아직 READY/PROCESSING"인 poison row를 재현한다. */
private fun forceAttempts(outbox: OutBox, attempts: Int) {
    val field = OutBox::class.java.getDeclaredField("attempts")
    field.isAccessible = true
    field.set(outbox, attempts)
}

class AbstractOutBoxSchedulerTest : BehaviorSpec({

    val repo = mockk<OutBoxRepository<TestOutBox, TestOutBoxHistory>>()
    val messageProducer = mockk<MessageProducer>()
    val transaction = mockk<TransactionTemplate>()

    every { transaction.execute<Any?>(any()) } answers {
        val callback = firstArg<TransactionCallback<Any?>>()
        callback.doInTransaction(mockk(relaxed = true))
    }
    every { repo.save(any()) } answers { firstArg() }
    every { repo.saveAll(any()) } answers { firstArg() }
    every { repo.deleteAll(any()) } returns Unit
    every { repo.saveAllHistory(any()) } returns Unit

    val scheduler = TestOutBoxProcessor(repo, messageProducer, transaction)

    Given("배치에 poison row(재시도 소진, 상태는 아직 READY)와 정상 row가 섞여 있으면") {
        val poison = TestOutBox("domain", "topic-poison", "{}", "key-1")
        forceAttempts(poison, OutBox.MAX_ATTEMPTS)

        val normal = TestOutBox("domain", "topic-normal", "{}", "key-2")

        every { repo.findToDispatch(any()) } returns listOf(poison, normal)
        every { messageProducer.produce(topic = "topic-normal", payload = any(), key = any()) } returns Unit

        scheduler.dispatchEvents()

        Then("poison row는 FAILED로 격리되고, 정상 row는 그대로 발행/완료된다") {
            poison.status shouldBe OutBoxStatus.FAILED
            normal.status shouldBe OutBoxStatus.COMPLETE
            verify(exactly = 1) { messageProducer.produce(topic = "topic-normal", payload = any(), key = any()) }
        }
    }

    Given("MQ 발행 도중 예외가 발생하면") {
        val failingEvent = TestOutBox("domain", "topic-fail", "{}", "key-fail")

        every { repo.findToDispatch(any()) } returns listOf(failingEvent)
        every { messageProducer.produce(topic = "topic-fail", payload = any(), key = any()) } throws RuntimeException("MQ 연결 오류")

        scheduler.dispatchEvents()

        Then("attempts가 증가하고 상태는 PROCESSING으로 유지되어 다음 배치에서 재시도한다") {
            failingEvent.attempts shouldBe 1
            failingEvent.status shouldBe OutBoxStatus.PROCESSING
        }
    }

    Given("처리 완료(COMPLETE) 및 실패(FAILED) 이벤트가 존재할 때") {
        val completedEvent = TestOutBox("domain", "topic-complete", "{}", "key-c").apply {
            dispatch()
            complete()
        }
        val failedEvent = TestOutBox("domain", "topic-failed", "{}", "key-f").apply {
            forceAttempts(this, OutBox.MAX_ATTEMPTS)
            fail()
        }

        every { repo.findAllProcessed(any()) } returnsMany listOf(
            listOf(completedEvent, failedEvent),
            emptyList()
        )

        Then("moveToHistory() 호출 시 OutBox에서 삭제되고 OutBoxHistory로 정상 이관된다") {
            scheduler.moveToHistory()

            verify(exactly = 1) { repo.deleteAll(listOf(completedEvent, failedEvent)) }
            verify(exactly = 1) { repo.saveAllHistory(any()) }
        }
    }
})

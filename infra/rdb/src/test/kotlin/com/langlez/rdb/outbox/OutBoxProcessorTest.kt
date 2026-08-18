package com.langlez.rdb.outbox

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.clients.producer.RecordMetadata
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.support.SendResult
import java.util.concurrent.CompletableFuture

@Entity
@Table(name = "test_outbox")
class TestOutBox(
    domain: String = "TEST",
    topic: String = "test-topic",
    payload: String = "{}",
    key: String? = "k1",
) : OutBox(domain, topic, payload, key)

/**
 * `threads` 를 override 하는 실제 사용 형태를 그대로 재현한다.
 * 베이스 생성자가 open val 을 읽으면 하위 클래스 백킹 필드가 아직 0이라 Semaphore(0) 이 된다.
 */
class TestProcessor(
    repo: OutBoxRepository<TestOutBox>,
    kafkaTemplate: KafkaTemplate<String, String>,
) : OutBoxProcessor<TestOutBox>(repo) {

    override val threads = 4
    override val threadTimeout = 2L // 버그가 있으면 빨리 드러나도록 짧게

    init {
        // @Autowired lateinit 을 테스트에서 대체
        val field = OutBoxProcessor::class.java.getDeclaredField("kafka")
        field.isAccessible = true
        field.set(this, kafkaTemplate)
    }
}

class OutBoxProcessorTest : BehaviorSpec({

    Given("threads 를 override 한 프로세서가 PENDING 이벤트를 하나 들고 있을 때") {
        // save 의 <S : T> 반환과 send 의 CompletableFuture<SendResult<..>> 는 타입 소거로
        // 명시 스텁이 까다로워 relaxed 로 두고, 호출 여부와 엔티티 상태만 검증한다.
        val repo = mockk<OutBoxRepository<TestOutBox>>(relaxed = true)
        val kafka = mockk<KafkaTemplate<String, String>>(relaxed = true)
        val outbox = TestOutBox()

        every { repo.fetch(any(), any()) } returns listOf(outbox)

        val sent = slot<ProducerRecord<String, String>>()
        // relaxed 자식 목은 제네릭 SendResult 를 못 만들어 get() 에서 터진다. 실제 future 를 준다.
        val ok: CompletableFuture<SendResult<String, String>> = CompletableFuture.completedFuture(
            SendResult(ProducerRecord("t", "k", "v"), mockk<RecordMetadata>(relaxed = true))
        )
        every { kafka.send(capture(sent)) } returns ok

        val processor = TestProcessor(repo, kafka)

        When("send() 를 실행하면") {
            processor.send()

            Then("카프카로 실제 발행된다") {
                // Semaphore(0) 이면 워커가 acquire 에서 영구 블록되어 send 가 호출되지 않는다
                verify(exactly = 1) { kafka.send(capture(sent)) }
                sent.captured.topic() shouldBe "test-topic"
            }

            Then("상태가 COMPLETE 로 저장된다") {
                outbox.status shouldBe OutBox.Status.COMPLETE
            }
        }
    }
})

package com.langlez.redis.stream

import com.langlez.core.MessageConsumer
import com.langlez.core.MessageSemantic
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.config.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.testcontainers.containers.GenericContainer

/** [MessageListenerRegistrar]가 @MessageConsumer 빈을 스캔해 컨슈머 그룹 생성, 신규 메시지 수신/ACK, PEL 복구(autoClaim)를 검증한다. */
class MessageListenerRegistrarTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val queue = RedisStreamMessageProducer(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    // =========================================================================
    // 1. 신규 메시지 수신 및 ACK 정상 유스케이스
    // =========================================================================
    Given("@MessageConsumer가 붙은 빈이 등록된 스프링 컨텍스트가 초기화되었을 때") {

        When("등록된 topic으로 메시지를 publish하면") {
            val topic = "registrar-test-topic"
            val group = "registrar-test-group"
            val received = CopyOnWriteArrayList<String>()
            val latch = CountDownLatch(1)

            val ctx = AnnotationConfigApplicationContext()
            ctx.registerBean(RedissonClient::class.java, Supplier { redissonClient })
            ctx.register(MessageListenerRegistrar::class.java)
            ctx.registerBean(RegistrarTestListener::class.java, Supplier { RegistrarTestListener(received, latch) })
            ctx.refresh()

            queue.produce(topic, "hello", "member-1")
            val completed = latch.await(15, TimeUnit.SECONDS)

            Then("리스너가 메시지를 수신해 핸들러를 호출하고, 처리 후 ACK하여 pending이 0이 된다") {
                completed shouldBe true
                received shouldBe listOf("hello")

                val pending = redissonClient.getStream<String, String>(topic).getPendingInfo(group)
                pending.total shouldBe 0
            }

            ctx.close()
        }
    }

    // =========================================================================
    // 2. 컨슈머 그룹 중복 초기화 (재시작 시나리오) 유스케이스
    // =========================================================================
    Given("이미 컨슈머 그룹이 존재하는 상태에서 (애플리케이션 재시작 시나리오)") {

        When("동일한 topic/group으로 MessageListenerRegistrar가 다시 초기화되면") {
            val topic = "registrar-restart-topic"
            val group = "registrar-restart-group"

            redissonClient.getStream<String, String>(topic)
                .createGroup(StreamCreateGroupArgs.name(group).makeStream())

            val received = CopyOnWriteArrayList<String>()
            val latch = CountDownLatch(1)

            val ctx = AnnotationConfigApplicationContext()
            ctx.registerBean(RedissonClient::class.java, Supplier { redissonClient })
            ctx.register(MessageListenerRegistrar::class.java)
            ctx.registerBean(RestartTestListener::class.java, Supplier { RestartTestListener(received, latch) })

            var thrown: Throwable? = null
            try {
                ctx.refresh()
            } catch (e: Throwable) {
                thrown = e
            }

            queue.produce(topic, "after-restart", "member-2")
            val completed = latch.await(15, TimeUnit.SECONDS)

            Then("BUSYGROUP 예외 없이 정상 초기화되고, 이후 메시지도 정상적으로 수신/ACK된다") {
                thrown shouldBe null
                completed shouldBe true
                received shouldBe listOf("after-restart")

                val pending = redissonClient.getStream<String, String>(topic).getPendingInfo(group)
                pending.total shouldBe 0
            }

            ctx.close()
        }
    }

    // =========================================================================
    // 3. ALO 모드 예외 발생 시 PEL 복구 (autoClaim) 유스케이스
    // =========================================================================
    Given("ALO 모드에서 메시지 처리 중 첫 시도에 예외가 발생하여 PEL에 메시지가 정체되었을 때") {

        When("processPendingMessages 복구 함수를 호출하면") {
            val topic = "pel-recovery-topic"
            val group = "pel-recovery-group"
            val received = CopyOnWriteArrayList<String>()
            val recoveryLatch = CountDownLatch(1)
            val failCount = AtomicInteger(0)

            val ctx = AnnotationConfigApplicationContext()
            ctx.registerBean(RedissonClient::class.java, Supplier { redissonClient })
            ctx.register(MessageListenerRegistrar::class.java)
            ctx.registerBean(AloRecoveryListener::class.java, Supplier { AloRecoveryListener(received, failCount, recoveryLatch) })
            ctx.refresh()

            val registrar = ctx.getBean(MessageListenerRegistrar::class.java)

            // 메시지 발행 (첫 수신 시 예외 발생으로 ACK 안 됨 -> PEL 정체)
            queue.produce(topic, "fail-then-recover", "member-3")
            
            // 1차 수신 및 예외 발생 대기
            Thread.sleep(1500)

            Then("초기 수신 시 예외로 인해 레디스 PEL(Pending List)에 1개의 미승인 메시지가 남아있다") {
                val pendingBefore = redissonClient.getStream<String, String>(topic).getPendingInfo(group)
                pendingBefore.total shouldBe 1
            }

            // PEL 복구 로직 강제 가동 (processPendingMessages)
            registrar.processPendingMessages()
            val recovered = recoveryLatch.await(10, TimeUnit.SECONDS)

            Then("PEL 복구 프로세스(autoClaim)가 미처리 메시지를 재획득하여 재처리에 성공하고 ACK 처리한다") {
                recovered shouldBe true
                received shouldBe listOf("fail-then-recover")

                val pendingAfter = redissonClient.getStream<String, String>(topic).getPendingInfo(group)
                pendingAfter.total shouldBe 0
            }

            ctx.close()
        }
    }

    // =========================================================================
    // 4. AMO 모드 예외 발생 시 PEL 복구 대상에서 제외 유스케이스
    // =========================================================================
    Given("AMO 모드에서 메시지 처리 중 예외가 발생할 때") {

        When("processPendingMessages 복구 함수를 실행해도") {
            val topic = "amo-exception-topic"
            val group = "amo-exception-group"
            val received = CopyOnWriteArrayList<String>()

            val ctx = AnnotationConfigApplicationContext()
            ctx.registerBean(RedissonClient::class.java, Supplier { redissonClient })
            ctx.register(MessageListenerRegistrar::class.java)
            ctx.registerBean(AmoExceptionListener::class.java, Supplier { AmoExceptionListener(received) })
            ctx.refresh()

            val registrar = ctx.getBean(MessageListenerRegistrar::class.java)

            // 메시지 발행 (AMO 모드 -> 수신 직후 ACK 날아가므로 PEL에 저장 안 됨)
            queue.produce(topic, "amo-fail-msg", "member-4")
            Thread.sleep(1500)

            Then("수신 즉시 ACK 처리되어 레디스 PEL에 메시지가 들어가지 않는다") {
                val pending = redissonClient.getStream<String, String>(topic).getPendingInfo(group)
                pending.total shouldBe 0
            }

            // 복구 함수 실행
            registrar.processPendingMessages()

            Then("AMO 메시지는 PEL 복구 재시도 대상에 포함되지 않는다") {
                received.size shouldBe 1
            }

            ctx.close()
        }
    }

    // =========================================================================
    // 5. 펜딩 메시지가 없는 정상 상태에서의 복구 스케줄러 수행 유스케이스
    // =========================================================================
    Given("모든 메시지가 정상 처리되어 PEL이 비어있는 상태일 때") {

        When("processPendingMessages 복구 함수를 실행하면") {
            val topic = "empty-pel-topic"
            val group = "empty-pel-group"
            val received = CopyOnWriteArrayList<String>()
            val latch = CountDownLatch(1)

            val ctx = AnnotationConfigApplicationContext()
            ctx.registerBean(RedissonClient::class.java, Supplier { redissonClient })
            ctx.register(MessageListenerRegistrar::class.java)
            ctx.registerBean(RegistrarTestListener2::class.java, Supplier { RegistrarTestListener2(received, latch) })
            ctx.refresh()

            val registrar = ctx.getBean(MessageListenerRegistrar::class.java)

            queue.produce(topic, "normal-msg", "member-5")
            latch.await(10, TimeUnit.SECONDS)

            Then("PEL 복구 함수가 에러나 예외 없이 안전하게 스킵된다") {
                var thrown: Throwable? = null
                try {
                    registrar.processPendingMessages()
                } catch (e: Throwable) {
                    thrown = e
                }
                thrown shouldBe null

                val pending = redissonClient.getStream<String, String>(topic).getPendingInfo(group)
                pending.total shouldBe 0
            }

            ctx.close()
        }
    }
})

private class RegistrarTestListener(
    private val received: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @MessageConsumer(topics = ["registrar-test-topic"], group = "registrar-test-group")
    fun onMessage(payload: String) {
        received += payload
        latch.countDown()
    }
}

private class RegistrarTestListener2(
    private val received: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @MessageConsumer(topics = ["empty-pel-topic"], group = "empty-pel-group")
    fun onMessage(payload: String) {
        received += payload
        latch.countDown()
    }
}

private class RestartTestListener(
    private val received: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @MessageConsumer(topics = ["registrar-restart-topic"], group = "registrar-restart-group")
    fun onMessage(payload: String) {
        received += payload
        latch.countDown()
    }
}

private class AloRecoveryListener(
    private val received: MutableList<String>,
    private val failCount: AtomicInteger,
    private val recoveryLatch: CountDownLatch,
) {
    @MessageConsumer(topics = ["pel-recovery-topic"], group = "pel-recovery-group", semantic = MessageSemantic.ALO)
    fun onMessage(payload: String) {
        if (failCount.getAndIncrement() == 0) {
            throw RuntimeException("Intentional first-time failure for PEL test")
        }
        received += payload
        recoveryLatch.countDown()
    }
}

private class AmoExceptionListener(
    private val received: MutableList<String>,
) {
    @MessageConsumer(topics = ["amo-exception-topic"], group = "amo-exception-group", semantic = MessageSemantic.AMO)
    fun onMessage(payload: String) {
        received += payload
        throw RuntimeException("Intentional AMO failure")
    }
}

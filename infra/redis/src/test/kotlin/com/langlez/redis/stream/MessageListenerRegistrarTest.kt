package com.langlez.redis.stream

import com.langlez.core.MessageListener
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.function.Supplier
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.api.stream.StreamCreateGroupArgs
import org.redisson.config.Config
import org.springframework.context.annotation.AnnotationConfigApplicationContext
import org.testcontainers.containers.GenericContainer

/** [MessageListenerRegistrar]가 @MessageListener 빈을 스캔해 실제로 컨슈머 그룹을 만들고 메시지를 수신/ACK하는지 검증한다. */
class MessageListenerRegistrarTest : BehaviorSpec({

    val redis = GenericContainer("redis:7.0").withExposedPorts(6379)
    redis.start()

    val redissonClient: RedissonClient = Redisson.create(
        Config().apply {
            useSingleServer().setAddress("redis://${redis.host}:${redis.getMappedPort(6379)}")
        }
    )

    val queue = RedisStreamMessageQueue(redissonClient)

    afterSpec {
        redissonClient.shutdown()
        redis.stop()
    }

    Given("@MessageListener가 붙은 빈이 등록된 스프링 컨텍스트가 초기화되었을 때") {

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

            queue.publish(topic, "hello", "member-1")
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

    Given("이미 컨슈머 그룹이 존재하는 상태에서 (애플리케이션 재시작 시나리오)") {

        When("동일한 topic/group으로 MessageListenerRegistrar가 다시 초기화되면") {
            val topic = "registrar-restart-topic"
            val group = "registrar-restart-group"

            // 이전 애플리케이션 인스턴스가 이미 만들어 둔 컨슈머 그룹 상황을 재현한다.
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

            queue.publish(topic, "after-restart", "member-2")
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
})

private class RegistrarTestListener(
    private val received: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @MessageListener(topics = ["registrar-test-topic"], group = "registrar-test-group")
    fun onMessage(payload: String) {
        received += payload
        latch.countDown()
    }
}

private class RestartTestListener(
    private val received: MutableList<String>,
    private val latch: CountDownLatch,
) {
    @MessageListener(topics = ["registrar-restart-topic"], group = "registrar-restart-group")
    fun onMessage(payload: String) {
        received += payload
        latch.countDown()
    }
}

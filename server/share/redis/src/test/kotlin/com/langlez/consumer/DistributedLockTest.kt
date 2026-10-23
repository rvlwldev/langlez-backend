package com.langlez.consumer

import com.langlez.redis.lock.LockAcquireFailedException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.util.concurrent.CompletableFuture

@SpringBootTest(classes = [ConsumerApp::class])
class DistributedLockTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var service: DemoService

    @Autowired
    lateinit var redisson: RedissonClient

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { TestRedis.host }
            registry.add("spring.data.redis.port") { TestRedis.port }
        }
    }

    init {
        Given("같은 키로 동시에 호출하면") {
            When("먼저 들어간 호출이 락을 쥐고 있는 동안 두 번째 호출이 오면") {
                Then("두 번째는 LockAcquireFailedException 이 나고 첫 번째는 정상 종료한다") {
                    val first = CompletableFuture.supplyAsync { service.work(1, 1500) }
                    Thread.sleep(400)

                    shouldThrow<LockAcquireFailedException> { service.work(1, 10) }
                    first.get() shouldBe "ok-1"
                }
            }
        }

        Given("다른 키로 동시에 호출하면") {
            When("한쪽이 락을 쥐고 있어도") {
                Then("서로 막지 않는다") {
                    val first = CompletableFuture.supplyAsync { service.work(2, 800) }
                    Thread.sleep(200)

                    service.work(3, 10) shouldBe "ok-3"
                    first.get() shouldBe "ok-2"
                }
            }
        }

        Given("작업이 끝난 뒤") {
            When("같은 키로 다시 호출하면") {
                Then("락이 풀려 있어 다시 들어갈 수 있다") {
                    service.work(4, 10) shouldBe "ok-4"
                    service.work(4, 10) shouldBe "ok-4"
                    redisson.keys.getKeysByPattern("lock:room:4").toList().shouldBeEmpty()
                }
            }
        }

        Given("작업 중 예외가 나면") {
            When("SpEL 키를 계산하지 못해 실패한 뒤 다른 키로 호출하면") {
                Then("락이 남지 않고 정상 호출이 된다") {
                    shouldThrow<IllegalStateException> { service.broken(1) }
                    service.work(5, 10) shouldBe "ok-5"
                }
            }
        }

        Given("throwOnFailure 가 꺼져 있을 때") {
            When("다른 호출이 락을 쥐고 있으면") {
                Then("실행하지 않고 null 을 돌려준다") {
                    val first = CompletableFuture.supplyAsync { service.skippable(6, 1000) }
                    Thread.sleep(300)

                    service.skippable(6, 10).shouldBeNull()
                    first.get() shouldBe "ran-6"
                }
            }
        }

        Given("LockKey 파라미터가 여러 개일 때") {
            When("락을 쥐고 있는 동안 Redis 키를 보면") {
                Then("파라미터 이름순으로 이어 붙은 키가 하나 있다") {
                    val running = CompletableFuture.supplyAsync { service.tagged("acme", 7, 1000) }
                    Thread.sleep(300)

                    redisson.keys.getKeysByPattern("lock:tag:*").toList() shouldBe listOf("lock:tag:7:acme")
                    running.get() shouldBe "acme-7"
                }
            }
        }

        Given("SpEL 키를 계산할 수 없을 때") {
            When("호출하면") {
                Then("전역 락으로 떨어지지 않고 어떤 식이 문제인지 알려 주며 실패한다") {
                    val error = shouldThrow<IllegalStateException> { service.broken(1) }
                    error.message!! shouldContain "#missing.name"
                }
            }
        }

        Given("재시도 횟수와 대기 간격이 설정돼 있을 때") {
            When("다른 호출이 잠깐 락을 쥐고 있으면") {
                Then("기다렸다가 락을 얻는다") {
                    val first = CompletableFuture.supplyAsync { service.retrying(8, 600) }
                    Thread.sleep(150)

                    service.retrying(8, 10) shouldBe "retried-8"
                    first.get() shouldBe "retried-8"
                }
            }
        }
    }
}

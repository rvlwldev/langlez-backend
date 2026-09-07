package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import com.langlez.chat.domain.SeqLockTimeoutException
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * B-02: 레디스 카운터가 없는 상태에서 여러 스레드가 동시에 `nextSeq` 를 불러도
 * 반환값이 단조 증가하고 중복이 없는지 본다.
 *
 * 예전 구현은 `incrementAndGet` 으로 먼저 1 을 뽑고 그 결과를 보고서야 Mongo 최대값으로
 * 되맞췄다. 그 왕복 사이에 다른 스레드가 이미 2, 3 을 들고 나가면 `compareAndSet` 이 실패해
 * 카운터가 낮은 값에 영구히 고정됐다 — 그러면 새 메시지가 기존 메시지와 같은 번호를 받는다.
 *
 * 행을 남기는 스펙이라 일반 CRUD 스펙(`ChatMessageRepositoryImplTest`)과 파일을 나눈다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000",
    ]
)
class ChatMessageRepositoryConcurrencyIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: ChatMessageRepository

    @Autowired
    lateinit var redisson: RedissonClient

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @JvmField
        val redis: GenericContainer<*> = GenericContainer(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379)
            .also { it.start() }

        @JvmField
        val mongo: MongoDBContainer = MongoDBContainer("mongo:6.0").also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.data.mongodb.uri") { mongo.replicaSetUrl }
        }
    }

    /** [threads] 개 스레드가 배리어로 시작 시점을 맞춰 동시에 `nextSeq` 를 부른다. */
    private fun callNextSeqInParallel(roomId: Long, threads: Int): List<Long> {
        val barrier = CyclicBarrier(threads)
        val results = Collections.synchronizedList(mutableListOf<Long>())

        (1..threads).map {
            Thread.ofVirtual().unstarted {
                barrier.await(10, TimeUnit.SECONDS)
                results.add(repo.nextSeq(roomId))
            }
        }.onEach(Thread::start).forEach { it.join(30_000) }

        return results
    }

    init {
        Given("이미 메시지 3건이 쌓인 방에서 레디스 카운터가 사라지면") {
            val roomId = 3001L
            repeat(3) { repo.save(ChatMessage(roomId, 1L, repo.nextSeq(roomId), ChatMessage.Type.TEXT, "m")) }
            redisson.getAtomicLong("chat:seq:$roomId").delete()

            val threadCount = 20
            val seqs = callNextSeqInParallel(roomId, threadCount)

            Then("모든 호출이 서로 다른 번호를 받는다 (중복 없음)") {
                seqs.toSet().size shouldBe threadCount
            }

            Then("기존 최대값(3) 다음부터 이어진다 (역행 없음)") {
                seqs.sorted() shouldBe (4L..(3 + threadCount)).toList()
            }
        }

        Given("메시지가 없던 새 방에서 처음부터 동시에 보내면") {
            val roomId = 3002L

            val threadCount = 20
            val seqs = callNextSeqInParallel(roomId, threadCount)

            Then("1부터 빈틈없이 이어진다") {
                seqs.sorted() shouldBe (1L..threadCount.toLong()).toList()
            }
        }

        /**
         * `tryLock(waitTime, unit)` 대신 `lock(leaseTime, unit)` 을 잘못 쓰면 waitTime 이 무제한이 되고
         * (그러면서 watchdog 도 꺼진다). 이 테스트는 락을 다른 스레드가 쥐고 절대 안 놓는 상태에서
         * `nextSeq` 를 불러 **무한 블로킹이 아니라 waitTime(10초) 안에 예외로 끝나는지**를 직접 잰다.
         * 시간이 지나야 드러나는 결함이라 `Thread.sleep()` 없이 `CountDownLatch.await(timeout)` 으로 재는
         * 것 자체가 검증 방법이다 — 무제한 대기 버그라면 이 latch 가 정해진 시간 안에 안 풀린다.
         */
        Given("초기화 락을 다른 스레드가 이미 쥐고 절대 풀지 않으면") {
            val roomId = 3003L
            // ChatMessageRepositoryImpl.seqInitLockKey 와 같은 포맷. private 이라 리터럴로 맞춘다.
            val lock = redisson.getLock("lock:chat-seq-init:$roomId")
            lock.lock() // 인자 없는 lock() 은 watchdog 이 살아 있어 자동 만료로 안 풀린다.

            val error = AtomicReference<Throwable?>()
            val done = CountDownLatch(1)
            val start = System.currentTimeMillis()

            Thread {
                try {
                    repo.nextSeq(roomId)
                } catch (e: Throwable) {
                    error.set(e)
                } finally {
                    done.countDown()
                }
            }.apply { isDaemon = true }.start()

            // waitTime(10초) + 여유. 무제한 대기 버그면 15초 안에 못 끝난다.
            val finishedInTime = done.await(15, TimeUnit.SECONDS)
            val elapsedMs = System.currentTimeMillis() - start
            lock.unlock()

            Then("무한 블로킹이 아니라 waitTime 안에 예외로 끝난다") {
                finishedInTime shouldBe true
                val thrown = error.get()
                thrown.shouldNotBeNull()
                thrown.shouldBeInstanceOf<SeqLockTimeoutException>()
            }

            Then("즉시 실패가 아니라 waitTime(10초) 근처까지 실제로 기다린 뒤 실패한다") {
                (elapsedMs in 9_000L..14_000L) shouldBe true
            }
        }
    }
}

package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
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
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.TimeUnit

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
    }
}

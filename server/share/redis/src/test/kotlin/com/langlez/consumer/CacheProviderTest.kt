package com.langlez.consumer

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import com.langlez.redis.cache.CacheProvider
import com.langlez.redis.cache.get
import com.langlez.redis.cache.getMany
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.longs.shouldBeBetween
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import org.redisson.Redisson
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.redisson.config.Config
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName
import tools.jackson.databind.json.JsonMapper
import java.net.ServerSocket
import java.time.LocalDateTime

data class Profile(val id: Long, val name: String, val tags: List<String>, val joinedAt: LocalDateTime)

@SpringBootTest(classes = [ConsumerApp::class])
class CacheProviderTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var provider: CacheProvider

    @Autowired
    lateinit var redisson: RedissonClient

    @Autowired
    lateinit var jsonMapper: JsonMapper

    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.data.redis.host") { TestRedis.host }
            registry.add("spring.data.redis.port") { TestRedis.port }
        }

        private val joinedAt = LocalDateTime.of(2026, 10, 23, 12, 30, 15)

        private fun profile(id: Long, name: String) = Profile(id, name, emptyList(), joinedAt)
    }

    init {
        Given("코틀린 데이터 클래스를 캐시에 넣으면") {
            When("다시 읽으면") {
                Then("JSON 으로 저장되고 값이 같은 새 인스턴스로 돌아온다") {
                    val cache = provider.getCache("profile")
                    val original = Profile(1, "하늘", listOf("ko", "en"), joinedAt)

                    cache.put(1L, original)

                    val read = cache.get<Profile>(1L)
                    read shouldBe original
                    (read === original) shouldBe false
                    redisson.getBucket<String>("profile:1").get() shouldContain "하늘"
                }
            }
        }

        Given("이름이 다른 두 캐시에") {
            When("같은 키를 넣으면") {
                Then("키 네임스페이스가 분리된다") {
                    provider.getCache("ns-a").put(1L, profile(1, "a"))

                    provider.getCache("ns-b").get<Profile>(1L).shouldBeNull()
                }
            }
        }

        Given("값을 넣으면") {
            When("Redis 의 TTL 을 보면") {
                Then("10분 안팎으로 설정된다") {
                    provider.getCache("ttl").put(1L, profile(1, "a"))

                    redisson.getBucket<String>("ttl:1").remainTimeToLive().shouldBeBetween(1, 610_000)
                }
            }
        }

        Given("여러 값을 한 번에 넣으면") {
            When("없는 키를 섞어 getMany 하면") {
                Then("있는 키만 돌려준다") {
                    val cache = provider.getCache("many")
                    cache.putMany(mapOf(1L to profile(1, "a"), 2L to profile(2, "b")))

                    val found = cache.getMany<Profile>(listOf(1L, 2L, 3L))

                    found.keys shouldBe setOf<Any>(1L, 2L)
                    found[2L]?.name shouldBe "b"
                }
            }
        }

        Given("이미 값이 있는 키에") {
            When("putIfAbsent 를 하면") {
                Then("덮어쓰지 않고 없는 키만 채운다") {
                    val cache = provider.getCache("absent")
                    cache.put(1L, profile(1, "원본"))

                    cache.putIfAbsent(1L, profile(1, "덮어쓰기"))
                    cache.putIfAbsent(2L, profile(2, "신규"))

                    cache.get<Profile>(1L)?.name shouldBe "원본"
                    cache.get<Profile>(2L)?.name shouldBe "신규"
                }
            }
        }

        Given("값이 들어 있을 때") {
            When("evict 와 evictMany 를 하면") {
                Then("지워진다") {
                    val cache = provider.getCache("evict")
                    cache.putMany(mapOf(1L to profile(1, "a"), 2L to profile(2, "b")))

                    cache.evict(1L)
                    cache.evictMany(listOf(2L))

                    cache.get<Profile>(1L).shouldBeNull()
                    cache.get<Profile>(2L).shouldBeNull()
                }
            }
        }

        Given("Redis 에 JSON 이 아닌 값이 들어 있으면") {
            When("읽으면") {
                Then("미스로 보고 Redis 장애로 오해하지 않는다") {
                    val cache = provider.getCache("corrupt")
                    redisson.getBucket<String>("corrupt:1").set("이건 JSON 이 아니다")

                    cache.get<Profile>(1L).shouldBeNull()

                    cache.put(2L, profile(2, "정상"))
                    redisson.getBucket<String>("corrupt:2").isExists shouldBe true
                }
            }
        }

        Given("Redis 가 멈췄다가 돌아오는 상황에서") {
            When("멈춘 동안 쓰고 읽으면") {
                Then("로컬 캐시로 대체하고, 복구되면 로컬 값을 버린다") {
                    val hostPort = ServerSocket(0).use { it.localPort }
                    val redis = GenericContainer(DockerImageName.parse("redis:7-alpine"))
                        .withExposedPorts(6379)
                        .withCreateContainerCmdModifier {
                            it.hostConfig!!.withPortBindings(PortBinding(Ports.Binding.bindPort(hostPort), ExposedPort(6379)))
                        }
                        .also { it.start() }
                    val config = Config().apply { codec = StringCodec.INSTANCE }
                    config.useSingleServer()
                        .setAddress("redis://${redis.host}:$hostPort")
                        .setTimeout(500)
                        .setConnectTimeout(500)
                        .setRetryAttempts(0)
                    val client = Redisson.create(config)
                    val resilient = CacheProvider(client, SimpleMeterRegistry(), jsonMapper)

                    try {
                        val cache = resilient.getCache("resilient")
                        cache.put(1L, profile(1, "정상"))
                        cache.get<Profile>(1L)?.name shouldBe "정상"

                        redis.dockerClient.stopContainerCmd(redis.containerId).exec()

                        cache.put(2L, profile(2, "장애중"))
                        cache.get<Profile>(2L)?.name shouldBe "장애중"

                        redis.dockerClient.startContainerCmd(redis.containerId).exec()

                        val deadline = System.currentTimeMillis() + 20_000
                        while (cache.get<Profile>(2L) != null && System.currentTimeMillis() < deadline) {
                            resilient.checkRedisHealth()
                            Thread.sleep(300)
                        }

                        cache.get<Profile>(2L).shouldBeNull()
                        cache.get<Profile>(1L) shouldNotBe null
                    } finally {
                        resilient.close()
                        client.shutdown()
                        redis.stop()
                    }
                }
            }
        }
    }
}

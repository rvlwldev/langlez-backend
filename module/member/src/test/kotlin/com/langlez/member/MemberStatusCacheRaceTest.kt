package com.langlez.member

import com.langlez.core.MemberStatusQuery
import com.langlez.core.cache.Cache
import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 캐시에 쓰려는 순간 리더 스레드를 붙잡아 두는 장치.
 *
 * 문제의 경합은 "커밋 전 DB 를 읽었지만 캐시 적재는 커밋 뒤에 도착"하는 순서에서만 터진다.
 * 그 순서는 타이밍에 맡기면 재현되지 않아서, 캐시 쓰기 지점을 직접 붙잡아 강제한다.
 */
private object StaleReader {
    const val THREAD = "stale-reader"

    val reachedCacheWrite = CountDownLatch(1)
    val released = CountDownLatch(1)

    fun gate() {
        if (Thread.currentThread().name != THREAD) return

        reachedCacheWrite.countDown()
        released.await(10, TimeUnit.SECONDS)
    }
}

/** 쓰기 지점만 [StaleReader] 에 물리고 나머지는 그대로 위임한다. */
private class GatedCache(private val delegate: Cache) : Cache {
    override fun <T : Any> get(key: Any, type: Class<T>): T? = delegate.get(key, type)
    override fun <T : Any> getMany(keys: Collection<Any>, type: Class<T>) = delegate.getMany(keys, type)

    override fun put(key: Any, value: Any) { StaleReader.gate(); delegate.put(key, value) }
    override fun <T : Any> putMany(entries: Map<out Any, T>) { StaleReader.gate(); delegate.putMany(entries) }
    override fun putIfAbsent(key: Any, value: Any) { StaleReader.gate(); delegate.putIfAbsent(key, value) }
    override fun <T : Any> putManyIfAbsent(entries: Map<out Any, T>) {
        StaleReader.gate()
        delegate.putManyIfAbsent(entries)
    }

    override fun evict(key: Any) = delegate.evict(key)
    override fun evictMany(keys: Collection<Any>) = delegate.evictMany(keys)
}

@TestConfiguration
class MemberStatusCacheRaceTestConfig {

    @Bean
    @Primary
    fun memberOnlineTracker(): MemberOnlineTracker = mockk(relaxed = true)

    @Bean
    @Primary
    fun gatedCacheProvider(@Qualifier("cacheProvider") delegate: CacheProvider): CacheProvider =
        object : CacheProvider {
            override fun getCache(name: String): Cache = delegate.getCache(name)
                .let { if (name == "member") GatedCache(it) else it }
        }
}

/**
 * 정지 커밋과 캐시 read-through 적재의 순서 경합.
 *
 * 정지 트랜잭션은 저장 시점에 `member:{id}` 를 지우고 갱신은 커밋 이후로 미룬다. 그 사이 들어온
 * 요청은 **아직 정지되지 않은 DB** 를 읽고, 그 값을 캐시에 적재한다. 이 적재가 커밋 후 갱신보다
 * 늦게 도착하면 정지가 캐시에서 통째로 지워진다. 상태 검사는 매 요청 이 캐시를 보므로
 * 정지된 회원이 그대로 서비스를 계속 쓴다.
 *
 * `MemberIntegrationTest` 의 순차 시나리오는 단일 스레드라 이 경로를 그냥 통과한다.
 * 일반 CRUD 테스트와 섞지 않고 파일을 나눈 이유다.
 */
@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
@Import(MemberStatusCacheRaceTestConfig::class)
class MemberStatusCacheRaceTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var memberService: MemberService

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var memberStatusQuery: MemberStatusQuery

    @Autowired
    lateinit var caches: CacheProvider

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

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    init {
        Given("정지 커밋과 캐시 적재가 겹칠 때") {
            val m = memberService.createMember(
                email = "race@test.com",
                providerType = Member.Provider.GOOGLE,
                providerId = "race1",
                providerUsername = "Race",
            )

            // 정지 트랜잭션이 저장 시점에 키를 지운 직후 상태 = 캐시 미스
            caches.getCache("member").evict(m.id)

            When("커밋 전 DB 를 읽은 요청의 캐시 적재가 커밋 뒤에 도착하면") {
                // 느린 리더. 캐시 미스로 DB(정지 전 상태)를 읽고 캐시 쓰기 직전에 멈춘다.
                val reader = Thread({ memberRepository.find(m.id) }, StaleReader.THREAD)
                reader.start()
                StaleReader.reachedCacheWrite.await(10, TimeUnit.SECONDS) shouldBe true

                // 그 사이 정지가 커밋돼 캐시에 최종 상태가 박힌다.
                memberService.suspendMember(m.id, reason = "race")

                // 이제서야 리더가 쥐고 있던 낡은 값이 캐시에 도착한다.
                StaleReader.released.countDown()
                reader.join(10_000)

                Then("상태 검사는 SUSPENDED 를 본다") {
                    memberStatusQuery.findStatus(m.id) shouldBe MemberStatusQuery.Status.SUSPENDED
                }

                Then("캐시에 남은 값도 SUSPENDED 다") {
                    caches.getCache("member").get<Member>(m.id)?.status shouldBe Member.Status.SUSPENDED
                }
            }
        }
    }
}

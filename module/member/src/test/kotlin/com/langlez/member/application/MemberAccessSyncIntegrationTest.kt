package com.langlez.member.application

import com.langlez.core.cache.CacheProvider
import com.langlez.core.cache.get
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.member.infrastructure.jpa.MemberJpaRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.redisson.api.RedissonClient
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * 온라인 트래커 동기화(syncAccessInfo)와 메타데이터 갱신(updateAccessInfo)의 통합 검증.
 *
 * 1. 동기화가 회원 캐시를 퇴출(evict/delete)시키지 않아 캐시 스탬피드를 유발하지 않는지
 * 2. ZSET 에서 0.0부터 end까지 정리되어 과거 지연 핑 누수가 남지 않는지
 * 3. DB 의 member_audits 에 접속 시각, IP, 기기 정보가 정상 반영되는지
 * 4. 과거 시각으로 업데이트 시도 시 최신 lastAccessedAt 이 롤백되지 않는지
 * 실제 PostgreSQL 및 Redis 컨테이너를 통해 검증한다.
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
class MemberAccessSyncIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: MemberRepository

    @Autowired
    lateinit var jpa: MemberJpaRepository

    @Autowired
    lateinit var tracker: MemberOnlineTracker

    @Autowired
    lateinit var caches: CacheProvider

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
        Given("회원이 로그인 및 접속 핑을 보낸 뒤 캐시에 적재된 상태에서") {
            val member = repo.save(
                Member(
                    email = "sync-test@test.com",
                    handle = "sync_user",
                    provider = Member.Provider.GOOGLE,
                    providerId = "sync-p1",
                )
            )

            // 캐시에 적재
            val cachedBefore = repo.find(member.id)
            cachedBefore.shouldNotBeNull()
            caches.getCache("member").get<Member>(member.id).shouldNotBeNull()

            tracker.recordAccess(member.id, ip = "192.168.1.100", deviceId = "device-uuid-1")
            tracker.toOnline(member.id)

            When("syncAccessInfo 가 실행되면") {
                tracker.syncAccessInfo()

                Then("회원 캐시는 불필요하게 퇴출되지 않고 유지된다 (캐시 스탬피드 방지)") {
                    val inCache = caches.getCache("member").get<Member>(member.id)
                    inCache.shouldNotBeNull()
                    inCache.id shouldBe member.id
                }

                Then("DB 에 접속 시각과 IP, 기기 정보가 정상 갱신된다") {
                    val fresh = jpa.findWithAuditById(member.id)
                    fresh.shouldNotBeNull()
                    fresh.audit.lastAccessedIp shouldBe "192.168.1.100"
                    fresh.audit.lastDeviceId shouldBe "device-uuid-1"
                    fresh.audit.lastAccessedAt.shouldNotBeNull()
                }

                Then("ZSET 의 핑 기록과 dirty 키가 깨끗이 정리된다") {
                    val zset = redisson.getScoredSortedSet<Long>("member:online-pings")
                    zset.count(0.0, true, Double.MAX_VALUE, true) shouldBe 0

                    val dirty = redisson.getSet<Long>("member:access:dirty")
                    dirty.readAll().contains(member.id) shouldBe false

                    val accessMap = redisson.getMap<String, String>("member:access:${member.id}")
                    accessMap.isExists shouldBe false
                }
            }

            When("이전 접속 시각보다 과거의 시각으로 updateAccessInfo 를 호출하면") {
                val currentAudit = jpa.findWithAuditById(member.id)!!.audit
                val currentAccessedAt = currentAudit.lastAccessedAt!!

                val pastTime = currentAccessedAt.minus(1, ChronoUnit.HOURS)
                repo.updateAccessInfo(
                    id = member.id,
                    accessedAt = pastTime,
                    ip = "10.0.0.1",
                    deviceId = null,
                )

                Then("더 최신인 접속 시각은 유지되고 IP만 갱신된다") {
                    val updated = jpa.findWithAuditById(member.id)!!.audit
                    updated.lastAccessedAt shouldBe currentAccessedAt
                    updated.lastAccessedIp shouldBe "10.0.0.1"
                    updated.lastDeviceId shouldBe "device-uuid-1"
                }
            }
        }
    }
}

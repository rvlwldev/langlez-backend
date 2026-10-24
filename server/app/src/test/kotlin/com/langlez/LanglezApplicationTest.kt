package com.langlez

import com.langlez.redis.cache.CacheProvider
import com.langlez.redis.lock.RedisLockService
import com.querydsl.jpa.impl.JPAQueryFactory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.mongodb.core.MongoTemplate
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.mongodb.MongoDBContainer
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = ["management.server.port=0"])
class LanglezApplicationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Value($$"${local.server.port}")
    private var port: Int = 0

    @Value($$"${local.management.port}")
    private var managementPort: Int = 0

    @Autowired
    lateinit var context: ApplicationContext

    companion object {
        private val postgres = PostgreSQLContainer("postgres:16").also { it.start() }
        private val mongo = MongoDBContainer("mongo:6.0").also { it.start() }
        private val redis = GenericContainer(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379).also { it.start() }
        private val kafka = KafkaContainer("apache/kafka:4.3.1").also { it.start() }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.mongodb.uri") { mongo.replicaSetUrl }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
            registry.add("spring.kafka.bootstrap-servers") { kafka.bootstrapServers }
            registry.add("langlez.auth.supabase-url") { "https://project-id.supabase.co" }
        }
    }

    private fun get(path: String, port: Int = this.port): HttpResponse<String> =
        HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("http://localhost:$port$path")).GET().build(),
            HttpResponse.BodyHandlers.ofString(),
        )

    init {
        Given("앱이 모든 인프라와 함께 떠 있을 때") {
            When("관리 포트에서 토큰 없이 liveness·readiness 를 호출하면") {
                Then("200 이고 상태는 UP 이다") {
                    listOf("/actuator/health/liveness", "/actuator/health/readiness").forEach { path ->
                        val response = get(path, managementPort)

                        response.statusCode() shouldBe 200
                        response.body() shouldContain "\"UP\""
                    }
                }
            }

            When("관리 포트에서 토큰 없이 Prometheus 지표를 긁으면") {
                Then("200 이고 지표가 나온다") {
                    val response = get("/actuator/prometheus", managementPort)

                    response.statusCode() shouldBe 200
                    response.body() shouldContain "jvm_memory_used_bytes"
                }
            }

            When("관리 포트에서 토큰 없이 그 밖의 actuator 를 호출하면") {
                Then("401 이다") {
                    listOf("/actuator/health", "/actuator/metrics", "/actuator/info").forEach { path ->
                        get(path, managementPort).statusCode() shouldBe 401
                    }
                }
            }

            When("서비스 포트에서 actuator 를 찾으면") {
                Then("서비스 포트에는 actuator 가 없다") {
                    get("/actuator/prometheus").statusCode() shouldBe 404
                }
            }

            When("토큰 없이 다른 경로를 호출하면") {
                Then("401 이다") {
                    get("/anything").statusCode() shouldBe 401
                }
            }

            When("컨텍스트의 빈을 보면") {
                Then("공유 모듈의 빈이 모두 올라와 있다") {
                    context.getBean(RedisLockService::class.java).shouldNotBeNull()
                    context.getBean(CacheProvider::class.java).shouldNotBeNull()
                    context.getBean(JPAQueryFactory::class.java).shouldNotBeNull()
                    context.getBean(MongoTemplate::class.java).shouldNotBeNull()
                    context.getBean(KafkaTemplate::class.java).shouldNotBeNull()
                }
            }
        }
    }
}

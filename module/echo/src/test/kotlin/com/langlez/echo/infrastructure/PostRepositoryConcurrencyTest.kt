package com.langlez.echo.infrastructure

import com.langlez.echo.domain.Post
import com.langlez.echo.domain.PostRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class PostRepositoryConcurrencyTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var postRepository: PostRepository

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @JvmField
        val redis: GenericContainer<*> = GenericContainer("redis:7.0")
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
        Given("동시성 테스트를 위한 게시물이 준비되었을 때") {
            val post = postRepository.save(Post(authorId = 1L, content = "동시성 테스트용 게시물"))
            val postId = post.id

            When("20개의 스레드가 동시에 좋아요 증가 메소드를 호출하면") {
                val executor = Executors.newFixedThreadPool(20)
                val latch = CountDownLatch(20)

                for (i in 1..20) {
                    executor.submit {
                        try {
                            postRepository.incrementLikeCount(postId)
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await(10, TimeUnit.SECONDS)
                executor.shutdown()

                Then("데이터베이스에서 다시 조회했을 때 좋아요 수가 20이어야 한다") {
                    val updatedPost = postRepository.findById(postId)
                    updatedPost shouldNotBe null
                    updatedPost!!.likeCount shouldBe 20L
                }
            }
        }

        Given("좋아요 수가 0인 게시물이 준비되었을 때") {
            val post = postRepository.save(Post(authorId = 1L, content = "감소 한계 테스트용 게시물"))
            val postId = post.id

            When("20개의 스레드가 동시에 좋아요 감소 메소드를 호출하면") {
                val executor = Executors.newFixedThreadPool(20)
                val latch = CountDownLatch(20)

                for (i in 1..20) {
                    executor.submit {
                        try {
                            postRepository.decrementLikeCount(postId)
                        } finally {
                            latch.countDown()
                        }
                    }
                }

                latch.await(10, TimeUnit.SECONDS)
                executor.shutdown()

                Then("좋아요 수는 음수가 되지 않고 0을 유지해야 한다") {
                    val updatedPost = postRepository.findById(postId)
                    updatedPost shouldNotBe null
                    updatedPost!!.likeCount shouldBe 0L
                }
            }
        }

        Given("블라인드 임계치 테스트를 위한 게시물이 준비되었을 때") {
            val post = postRepository.save(Post(authorId = 1L, content = "블라인드 테스트용 게시물"))
            val postId = post.id

            When("신고 횟수를 증가시키고 블라인드 임계치 도달 여부를 확인하면") {
                for (i in 1..Post.BLIND_THRESHOLD) {
                    postRepository.incrementReportCount(postId)
                    postRepository.blindIfThresholdReached(postId, Post.BLIND_THRESHOLD)
                }

                val postAfterThreshold = postRepository.findById(postId)
                postAfterThreshold shouldNotBe null
                val firstBlindedAt = postAfterThreshold!!.blindedAt

                Then("게시물이 블라인드 처리되고 blindedAt이 설정되어야 한다") {
                    postAfterThreshold.blinded shouldBe true
                    firstBlindedAt shouldNotBe null
                }

                TimeUnit.MILLISECONDS.sleep(100)
                postRepository.incrementReportCount(postId)
                postRepository.blindIfThresholdReached(postId, Post.BLIND_THRESHOLD)

                val postAfterExtraReport = postRepository.findById(postId)

                Then("이미 블라인드된 게시물은 blindedAt 시간이 갱신되지 않아야 한다") {
                    postAfterExtraReport shouldNotBe null
                    postAfterExtraReport!!.blindedAt shouldBe firstBlindedAt
                }
            }
        }
    }
}

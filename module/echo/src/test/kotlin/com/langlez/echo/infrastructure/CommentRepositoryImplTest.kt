package com.langlez.echo.infrastructure

import com.langlez.echo.domain.*
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberProvider
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.PostgreSQLContainer

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
class CommentRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var commentRepository: CommentRepository

    @Autowired
    lateinit var postRepository: PostRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

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

        @JvmField
        val mongodb: MongoDBContainer = MongoDBContainer("mongo:6.0")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    init {
        Given("CommentRepository 가 주어졌을 때") {
            val user = memberRepository.save(
                Member(
                    email = "commenter@test.com",
                    username = "commenter",
                    nickname = "Commenter",
                    provider = MemberProvider.GOOGLE,
                    providerId = "g-commenter",
                    providerDisplayName = "Commenter"
                )
            )

            val post = postRepository.save(Post(authorId = user.id, content = "Post for comments"))

            When("댓글을 저장하고 조회할 때") {
                val c1 = commentRepository.save(Comment(postId = post.id, authorId = user.id, content = "Comment 1"))
                val c2 = commentRepository.save(Comment(postId = post.id, authorId = user.id, content = "Comment 2"))
                val c3 = commentRepository.save(Comment(postId = post.id, authorId = user.id, content = "Comment 3"))

                Then("count 가 올바르게 집계되어야 한다") {
                    commentRepository.countByPost(post.id) shouldBe 3L
                }

                Then("cursor 기반 페이지네이션이 역순(최신순)으로 정상 동작해야 한다") {
                    val page1 = commentRepository.findByPost(post.id, null, 2)
                    page1 shouldHaveSize 2
                    page1[0].id shouldBe c3.id
                    page1[1].id shouldBe c2.id

                    val page2 = commentRepository.findByPost(post.id, page1.last().id, 2)
                    page2 shouldHaveSize 1
                    page2[0].id shouldBe c1.id
                }
            }
        }
    }
}

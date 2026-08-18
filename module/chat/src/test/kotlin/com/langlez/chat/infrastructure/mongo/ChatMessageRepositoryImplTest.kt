package com.langlez.chat.infrastructure.mongo

import com.langlez.chat.domain.ChatMessage
import com.langlez.chat.domain.ChatMessageRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
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

@SpringBootTest(
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        // 문서에 붙인 인덱스 정의가 실제로 만들어지는지까지 확인한다(부분 인덱스 문법 오류를 여기서 잡는다).
        "spring.data.mongodb.auto-index-creation=true",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
class ChatMessageRepositoryImplTest : BehaviorSpec() {

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

        // MongoDBContainer 는 단일 노드 레플리카셋으로 뜬다.
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

    init {
        Given("사진 여러 장을 담은 메시지를 저장하면") {
            val roomId = 2001L
            val files = listOf(
                ChatMessage.Attachment("https://cdn/1.jpg", 0),
                ChatMessage.Attachment("https://cdn/2.jpg", 1),
            )
            val saved = repo.save(
                ChatMessage(roomId, 1L, repo.nextSeq(roomId), ChatMessage.Type.IMAGE, files = files)
            )

            Then("조회 한 번으로 첨부까지 함께 복원된다 (조인이 없다)") {
                val found = repo.find(saved.id.shouldNotBeNull()).shouldNotBeNull()

                found.files.map { it.url } shouldBe files.map { it.url }
                found.files.map { it.sequence } shouldBe listOf(0, 1)
            }

            Then("저장 직후에는 미발행 상태다") {
                saved.published shouldBe false
            }
        }

        Given("한 방에 메시지가 여러 건 쌓이면") {
            val roomId = 2002L
            val seqs = (1..5).map {
                repo.save(ChatMessage(roomId, 1L, repo.nextSeq(roomId), ChatMessage.Type.TEXT, "m$it")).seq
            }

            Then("seq 는 방마다 1 부터 단조 증가한다") {
                seqs shouldBe listOf(1L, 2L, 3L, 4L, 5L)
            }

            Then("seq 내림차순으로 돌아온다") {
                repo.findByRoom(roomId, 10, null).map { it.seq } shouldBe seqs.reversed()
            }

            Then("커서로 다음 장을 이어 받는다") {
                val first = repo.findByRoom(roomId, 2, null)
                first.map { it.seq } shouldBe listOf(5L, 4L)

                repo.findByRoom(roomId, 2, first.last().seq).map { it.seq } shouldBe listOf(3L, 2L)
            }

            Then("다른 방 메시지는 섞이지 않는다") {
                repo.findByRoom(9999L, 10, null) shouldBe emptyList()
            }
        }

        Given("레디스 카운터가 사라진 뒤 다시 보내면") {
            val roomId = 2004L
            repeat(3) { repo.save(ChatMessage(roomId, 1L, repo.nextSeq(roomId), ChatMessage.Type.TEXT, "m")) }
            redisson.getAtomicLong("chat:seq:$roomId").delete()

            // 카운터가 1 로 되돌아가면 새 메시지가 옛 메시지 아래로 정렬돼 대화 순서가 통째로 뒤집힌다.
            Then("이미 저장된 최대 seq 다음 번호를 준다") {
                repo.nextSeq(roomId) shouldBe 4L
            }
        }

        Given("일부 메시지만 발행되면") {
            val roomId = 2003L
            val unpublished = repo.save(ChatMessage(roomId, 1L, repo.nextSeq(roomId), ChatMessage.Type.TEXT, "a"))
            val published = repo.save(ChatMessage(roomId, 1L, repo.nextSeq(roomId), ChatMessage.Type.TEXT, "b"))
            repo.save(published.apply { markPublished() })

            Then("미발행 메시지만 돌아온다") {
                val ids = repo.findUnpublished(100).map { it.id }

                ids shouldContain unpublished.id
                ids shouldNotContain published.id
            }
        }
    }
}

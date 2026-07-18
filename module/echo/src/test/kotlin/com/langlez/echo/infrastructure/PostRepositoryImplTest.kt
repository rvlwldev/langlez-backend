package com.langlez.echo.infrastructure

import com.langlez.echo.domain.*
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.MongoDBContainer
import org.testcontainers.containers.MySQLContainer
import java.time.Instant

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=create-drop"
    ]
)
class PostRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var postRepository: PostRepository

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    companion object {
        @JvmField
        val mysql: MySQLContainer<*> = MySQLContainer("mysql:8.0")
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
            registry.add("spring.datasource.url") { mysql.jdbcUrl + "?serverTimezone=Asia/Seoul&characterEncoding=UTF-8" }
            registry.add("spring.datasource.username") { mysql.username }
            registry.add("spring.datasource.password") { mysql.password }
            registry.add("spring.data.mongodb.uri") { mongodb.replicaSetUrl }
            registry.add("spring.data.redis.host") { redis.host }
            registry.add("spring.data.redis.port") { redis.getMappedPort(6379) }
        }
    }

    init {
        Given("PostRepository 가 주어졌을 때") {
            val userA = memberRepository.save(
                Member(
                    email = "usera@test.com",
                    username = "usera",
                    nickname = "User A",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-usera",
                    providerDisplayName = "User A"
                )
            )

            val userB = memberRepository.save(
                Member(
                    email = "userb@test.com",
                    username = "userb",
                    nickname = "User B",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-userb",
                    providerDisplayName = "User B"
                )
            )

            val userC = memberRepository.save(
                Member(
                    email = "userc@test.com",
                    username = "userc",
                    nickname = "User C",
                    provider = Member.Provider.GOOGLE,
                    providerId = "g-userc",
                    providerDisplayName = "User C"
                )
            )

            When("팔로잉 피드를 조회할 때") {
                val p1 = postRepository.save(Post(authorId = userA.id, content = "P1"))
                val p2 = postRepository.save(Post(authorId = userA.id, content = "P2"))
                val p3 = postRepository.save(Post(authorId = userA.id, content = "P3"))

                Then("cursor 기반 페이지네이션이 정확히 동작해야 한다") {
                    val feed1 = postRepository.findFollowingFeed(listOf(userA.id), null, 2)
                    feed1 shouldHaveSize 2
                    feed1[0].id shouldBe p3.id
                    feed1[1].id shouldBe p2.id

                    val feed2 = postRepository.findFollowingFeed(listOf(userA.id), feed1.last().id, 2)
                    feed2 shouldHaveSize 1
                    feed2[0].id shouldBe p1.id
                }

                Then("blinded=true 인 글은 조회에서 제외되어야 한다") {
                    val p4 = Post(authorId = userA.id, content = "P4")
                    repeat(5) { p4.increaseReportCount() } // blind threshold is 5
                    p4.blinded shouldBe true
                    postRepository.save(p4)

                    val feed = postRepository.findFollowingFeed(listOf(userA.id), null, 10)
                    feed.any { it.id == p4.id } shouldBe false
                }
            }

            When("추천 피드를 조회할 때") {
                val p1 = Post(authorId = userA.id, content = "Rec1")
                repeat(10) { p1.increaseLikeCount() } // like 10
                val savedP1 = postRepository.save(p1)

                val p2 = Post(authorId = userB.id, content = "Rec2")
                repeat(20) { p2.increaseLikeCount() } // like 20
                val savedP2 = postRepository.save(p2)

                val p3 = Post(authorId = userC.id, content = "Rec3")
                repeat(5) { p3.increaseLikeCount() } // like 5
                val savedP3 = postRepository.save(p3)

                Then("좋아요 개수 내림차순 및 커서 페이지네이션이 정확히 동작해야 한다") {
                    val feed1 = postRepository.findRecommendedFeed(emptyList(), null, 2)
                    feed1 shouldHaveSize 2
                    feed1[0].id shouldBe savedP2.id
                    feed1[1].id shouldBe savedP1.id

                    // 이 Given 블록 내 다른 When(팔로잉 피드 등)에서 만들어진 좋아요 0개짜리 글들도
                    // 같은 spec 인스턴스 내에서 누적되어 추천 피드에 섞여 들어올 수 있으므로,
                    // 다음 페이지의 정확한 총 개수 대신 "커서 이후 좋아요 상위 글이 정확히 이어지는지"와
                    // "이전 페이지와 중복이 없는지"만 검증한다.
                    val feed2 = postRepository.findRecommendedFeed(emptyList(), feed1.last().id, 2)
                    feed2.shouldNotBeEmpty()
                    feed2[0].id shouldBe savedP3.id
                    val feed1Ids = feed1.map { it.id }.toSet()
                    feed2.none { it.id in feed1Ids } shouldBe true
                }

                Then("제외 작가(excludeAuthorIds)의 글은 제외되어야 한다") {
                    val feed = postRepository.findRecommendedFeed(listOf(userB.id), null, 10)
                    feed.any { it.id == savedP2.id } shouldBe false
                    feed.any { it.id == savedP1.id } shouldBe true
                }

                Then("blinded=true 인 글은 조회에서 제외되어야 한다") {
                    val p4 = Post(authorId = userA.id, content = "Rec4")
                    repeat(30) { p4.increaseLikeCount() }
                    repeat(5) { p4.increaseReportCount() }
                    p4.blinded shouldBe true
                    postRepository.save(p4)

                    val feed = postRepository.findRecommendedFeed(emptyList(), null, 10)
                    feed.any { it.id == p4.id } shouldBe false
                }
            }

            When("해시태그 피드를 조회할 때") {
                val tag = postRepository.saveHashtag(Hashtag(name = "kotlin"))
                val p1 = postRepository.save(Post(authorId = userA.id, content = "TagP1"))
                val p2 = postRepository.save(Post(authorId = userA.id, content = "TagP2"))

                postRepository.savePostHashtag(PostHashtag(postId = p1.id, hashtagId = tag.id))
                postRepository.savePostHashtag(PostHashtag(postId = p2.id, hashtagId = tag.id))

                Then("해당 태그 글들이 정확히 커서 페이지네이션 되어야 한다") {
                    val feed1 = postRepository.findByHashtag("kotlin", null, 1)
                    feed1 shouldHaveSize 1
                    feed1[0].id shouldBe p2.id

                    val feed2 = postRepository.findByHashtag("kotlin", feed1.last().id, 1)
                    feed2 shouldHaveSize 1
                    feed2[0].id shouldBe p1.id
                }

                Then("blinded=true 인 글은 해시태그 조회에서 제외되어야 한다") {
                    val p3 = Post(authorId = userA.id, content = "TagP3")
                    repeat(5) { p3.increaseReportCount() }
                    postRepository.save(p3)
                    postRepository.savePostHashtag(PostHashtag(postId = p3.id, hashtagId = tag.id))

                    val feed = postRepository.findByHashtag("kotlin", null, 10)
                    feed.any { it.id == p3.id } shouldBe false
                }
            }

            When("포스트 미디어를 일괄 저장 및 조회할 때") {
                val p = postRepository.save(Post(authorId = userA.id, content = "MediaPost"))
                val mediaList = listOf(
                    PostMedia(postId = p.id, url = "https://cdn/1.jpg", type = PostMedia.Type.IMAGE, sequence = 1),
                    PostMedia(postId = p.id, url = "https://cdn/2.mp4", type = PostMedia.Type.VIDEO, sequence = 2)
                )
                postRepository.saveMediaAll(mediaList)

                Then("postId 리스트로 미디어가 정상 조회 매핑되어야 한다") {
                    val foundMedia = postRepository.findMediaByPostIds(listOf(p.id))
                    foundMedia shouldHaveSize 2
                    foundMedia.any { it.url == "https://cdn/1.jpg" } shouldBe true
                    foundMedia.any { it.url == "https://cdn/2.mp4" } shouldBe true
                }
            }

            When("좋아요를 저장, 조회, 삭제할 때") {
                val p = postRepository.save(Post(authorId = userA.id, content = "LikePost"))
                val like = PostLike(postId = p.id, memberId = userB.id)

                Then("정상적으로 흐름이 처리되어야 한다") {
                    postRepository.saveLike(like)
                    val found = postRepository.findLike(userB.id, p.id)
                    found shouldNotBe null
                    found!!.postId shouldBe p.id

                    // deleteLike 는 파생 삭제 쿼리라 트랜잭션이 필요하다 (실제 운영에서는 서비스 계층의
                    // @Transactional 경계 안에서 호출되므로, 테스트에서도 동일한 트랜잭션 경계를 제공한다).
                    transactionTemplate.execute { postRepository.deleteLike(userB.id, p.id) }
                    postRepository.findLike(userB.id, p.id) shouldBe null
                }
            }

            When("신고를 저장, 조회할 때") {
                val p = postRepository.save(Post(authorId = userA.id, content = "ReportPost"))
                val report = PostReport(postId = p.id, reporterId = userB.id, reason = "SPAM")

                Then("정상적으로 저장 및 조회가 가능해야 한다") {
                    postRepository.saveReport(report)
                    val found = postRepository.findReport(userB.id, p.id)
                    found shouldNotBe null
                    found!!.reason shouldBe "SPAM"
                }
            }
        }
    }
}

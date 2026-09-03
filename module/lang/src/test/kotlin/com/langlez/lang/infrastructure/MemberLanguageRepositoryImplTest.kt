package com.langlez.lang.infrastructure

import com.langlez.lang.contract.LanguageReader
import com.langlez.lang.domain.MemberLanguage
import com.langlez.lang.domain.MemberLanguage.Level
import com.langlez.lang.domain.MemberLanguage.Role
import com.langlez.lang.domain.MemberLanguageRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName

/**
 * 상호보완 질의는 셀프 조인이라 목으로는 아무것도 검증되지 않는다. 실제 DB 로 돌린다.
 * 유니크 제약(member_id, language)도 여기서만 확인된다 — 엔티티 불변식은 role 만 본다.
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
class MemberLanguageRepositoryImplTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var repo: MemberLanguageRepository

    @Autowired
    lateinit var langs: LanguageReader

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
        fun native(memberId: Long, language: String) =
            MemberLanguage(memberId = memberId, language = language, role = Role.NATIVE)

        fun learning(memberId: Long, language: String, level: Level = Level.INTERMEDIATE) =
            MemberLanguage(memberId = memberId, language = language, role = Role.LEARNING, level = level)

        Given("같은 언어를 모국어와 학습언어로 둘 다 저장하려 하면") {
            val me = 7001L
            repo.saveAll(listOf(native(me, "ko")))

            Then("유니크 제약이 막는다 — role 이 키에 없어서다") {
                shouldThrow<DataIntegrityViolationException> {
                    repo.saveAll(listOf(learning(me, "ko")))
                }
            }
        }

        Given("한국어 모국어 / 영어 학습자인 내가 후보를 찾을 때") {
            val me = 7101L
            repo.saveAll(listOf(native(me, "ko"), learning(me, "en")))

            // 상호보완: 영어가 모국어이고 한국어를 배운다
            val complement = 7102L
            repo.saveAll(listOf(native(complement, "en"), learning(complement, "ko")))

            // 한 방향만 성립: 영어가 모국어지만 배우는 건 일본어다
            val oneWay = 7103L
            repo.saveAll(listOf(native(oneWay, "en"), learning(oneWay, "ja")))

            // 반대 한 방향만 성립: 한국어를 배우지만 모국어가 일본어다
            val reverseOnly = 7104L
            repo.saveAll(listOf(native(reverseOnly, "ja"), learning(reverseOnly, "ko")))

            val candidates = repo.findComplementaryCandidates(
                myNativeLanguages = setOf("ko"),
                myLearningLanguages = setOf("en"),
                excludeMemberId = me,
                limit = 100,
            )

            Then("양방향이 성립하는 회원만 나온다") {
                candidates shouldContainExactlyInAnyOrder listOf(complement)
            }

            Then("한 방향만 성립하는 회원은 빠진다") {
                candidates shouldNotContain oneWay
                candidates shouldNotContain reverseOnly
            }

            Then("나 자신은 빠진다") {
                candidates shouldNotContain me
            }

            Then("포트도 같은 결과를 돌려준다") {
                langs.complementaryCandidates(
                    myNativeLanguages = setOf("ko"),
                    myLearningLanguages = setOf("en"),
                    excludeMemberId = me,
                    limit = 100,
                ) shouldContainExactlyInAnyOrder listOf(complement)
            }
        }

        Given("상대가 여러 언어로 겹쳐 맞으면") {
            val me = 7201L
            repo.saveAll(listOf(native(me, "ko"), native(me, "ja"), learning(me, "en"), learning(me, "fr")))

            val many = 7202L
            repo.saveAll(
                listOf(
                    native(many, "en"), native(many, "fr"),
                    learning(many, "ko"), learning(many, "ja"),
                )
            )

            Then("id 가 중복되지 않는다 — distinct 가 없으면 limit 이 회원 수가 아니라 쌍 수를 자른다") {
                repo.findComplementaryCandidates(
                    myNativeLanguages = setOf("ko", "ja"),
                    myLearningLanguages = setOf("en", "fr"),
                    excludeMemberId = me,
                    limit = 100,
                ).count { it == many } shouldBe 1
            }
        }

        Given("모국어나 학습언어가 비어 있으면") {
            Then("질의를 돌리지 않고 빈 목록이다") {
                repo.findComplementaryCandidates(emptySet(), setOf("en"), 7301L, 100) shouldBe emptyList()
                repo.findComplementaryCandidates(setOf("ko"), emptySet(), 7301L, 100) shouldBe emptyList()
            }
        }

        Given("언어를 저장한 뒤") {
            val me = 7401L
            repo.saveAll(listOf(native(me, "ko"), learning(me, "en", Level.ADVANCED)))

            Then("배치 조회가 회원별로 묶어서 돌려준다") {
                val found = langs.languagesOf(listOf(me, 7402L))
                found.keys shouldBe setOf(me)
                found.getValue(me).map { it.language } shouldContainExactlyInAnyOrder listOf("ko", "en")
            }

            Then("학습언어만 레벨을 갖는다") {
                val found = langs.languagesOf(me).associateBy { it.language }
                found.getValue("ko").level shouldBe null
                found.getValue("en").level shouldBe LanguageReader.Level.ADVANCED
            }

            Then("전체 삭제 후 다시 저장하면 그것만 남는다") {
                repo.deleteAll(me)
                repo.saveAll(listOf(native(me, "ja")))

                repo.findAll(me).map { it.language } shouldBe listOf("ja")
            }
        }
    }
}

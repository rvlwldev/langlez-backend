package com.langlez.member

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.member.contract.MemberQuery
import com.langlez.member.contract.MemberCreatedEvent
import com.langlez.member.contract.MemberHandleChangedEvent
import com.langlez.member.contract.MemberWithdrawnEvent
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.domain.MemberRepository
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.infrastructure.jpa.MemberOutBoxRepository
import com.langlez.rdb.outbox.OutBox
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.context.annotation.Primary
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.utility.DockerImageName
import java.time.Instant
import java.time.temporal.ChronoUnit

@TestConfiguration
class MemberIntegrationTestConfig {
    @Bean
    @Primary
    fun memberOnlineTracker(): MemberOnlineTracker = mockk(relaxed = true)
}

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "jwt.secret=dGhpcy1pcy1hLXNlY3JldC1rZXktZm9yLWp3dC1zaWduaW5nLXBsZWFzZS1rZWVwLWl0LXNhZmUtYW5kLXNlY3VyZQ==",
        "jwt.access-token-ttl-secs=3600",
        "jwt.refresh-token-ttl-secs=86400",
        "spring.main.allow-bean-definition-overriding=true",
        "spring.jpa.hibernate.ddl-auto=validate",
        "app.cors.allowed-origins=http://localhost:3000"
    ]
)
@Import(MemberIntegrationTestConfig::class)
class MemberIntegrationTest : BehaviorSpec() {

    override fun extensions() = listOf(SpringExtension)

    @Autowired
    lateinit var memberService: MemberService

    @Autowired
    lateinit var memberRepository: MemberRepository

    @Autowired
    lateinit var outboxJpaRepository: MemberOutBoxRepository

    @Autowired
    lateinit var memberOnlineTracker: MemberOnlineTracker

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var memberStatusQuery: MemberQuery

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        // 로컬에 레디스가 떠 있기를 기대하면 안 된다. DB 처럼 컨테이너로 띄운다.
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
        // =========================================================================
        // 1. 회원 가입 (생성) 유스케이스
        // =========================================================================
        Given("회원 가입 시") {

            When("정상적인 정보로 회원 가입에 성공하면") {
                val beforeMemberCount = memberRepository.count()
                val beforeOutboxCount = outboxJpaRepository.count()

                val result = memberService.createMember(
                    email = "create_test@example.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "g123_test",
                    providerUsername = "Test User",
                )

                Then("DB에 회원 정보가 저장된다") {
                    result.id shouldNotBe null
                    val found = memberRepository.find(result.id)
                    found shouldNotBe null
                    found?.email shouldBe "create_test@example.com"
                    found?.handle shouldBe result.handle
                }

                Then("멤버 총 카운트가 1 증가한다") {
                    memberRepository.count() shouldBe beforeMemberCount + 1
                }

                Then("이벤트 아웃박스(member_event_outbox)에 member-created 레코드가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                    val createdOutbox = outboxJpaRepository.findAll().last()
                    createdOutbox.domain shouldBe "MEMBER"
                    createdOutbox.topic shouldBe "member-created"
                    createdOutbox.status shouldBe OutBox.Status.PENDING
                    createdOutbox.key shouldBe result.id.toString()

                    val eventPayload = objectMapper.readValue(createdOutbox.payload, MemberCreatedEvent::class.java)
                    eventPayload.id shouldBe result.id
                    eventPayload.email shouldBe "create_test@example.com"
                }
            }

            When("이메일/핸들 중복 등 DB 제약 위반으로 회원 가입에 실패하면") {
                val beforeMemberCount = memberRepository.count()
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("DB 제약위반 예외(DataIntegrityViolationException)가 발생한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        memberService.createMember(
                            email = "create_test@example.com",
                            providerType = Member.Provider.GOOGLE,
                            providerId = "g123_test",
                            providerUsername = "Test User",
                        )
                    }
                }

                Then("멤버 카운트가 증가하지 않는다") {
                    memberRepository.count() shouldBe beforeMemberCount
                }

                Then("이벤트 아웃박스 레코드가 생성되지 않고 롤백된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }
        }

        // =========================================================================
        // 2. 핸들(핸들네임) 변경 유스케이스
        // =========================================================================
        Given("핸들 변경 시") {

            When("유효한 핸들로 변경에 성공하면") {
                val m = memberService.createMember(
                    email = "user2@example.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "g456_test",
                    providerUsername = "User2",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                val updated = memberService.updateHandle(m.id, "user2_after")

                Then("핸들이 변경된다") {
                    updated.handle shouldBe "user2_after"
                    val found = memberRepository.find(m.id)
                    found?.handle shouldBe "user2_after"
                }

                Then("lastHandleUpdatedAt 타임스탬프가 업데이트된다") {
                    val found = memberRepository.find(m.id)
                    found?.audit?.lastHandleUpdatedAt shouldNotBe null
                }

                Then("이벤트 아웃박스에 member-handle-changed 레코드가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                    val outbox = outboxJpaRepository.findAll().last()
                    outbox.topic shouldBe "member-handle-changed"
                    outbox.key shouldBe m.id.toString()

                    val eventPayload = objectMapper.readValue(outbox.payload, MemberHandleChangedEvent::class.java)
                    eventPayload.id shouldBe m.id
                    eventPayload.newHandle shouldBe "user2_after"
                }
            }

            When("이미 사용 중인 핸들로 변경에 실패하면") {
                val m1 = memberService.createMember(
                    email = "u1@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p1",
                    providerUsername = "U1",
                )
                val m2 = memberService.createMember(
                    email = "u2@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p2",
                    providerUsername = "U2",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("409 CONFLICT (member.handle.duplicated) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateHandle(m2.id, m1.handle)
                    }
                    ex.status.value() shouldBe 409
                    ex.message shouldBe "member.handle.duplicated"
                }

                Then("회원 핸들이 변경되지 않고 이전 상태를 유지한다") {
                    memberRepository.find(m2.id)?.handle shouldBe m2.handle
                }

                Then("이벤트 아웃박스 생성이 롤백된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }

            When("형식이 유효하지 않은 핸들(2자 미만)으로 변경에 실패하면") {
                val m = memberService.createMember(
                    email = "u3@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p3",
                    providerUsername = "U3",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("400 BAD_REQUEST (member.handle.invalid) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateHandle(m.id, "a")
                    }
                    ex.status.value() shouldBe 400
                    ex.message shouldBe "member.handle.invalid"
                }

                Then("아웃박스가 생성되지 않고 롤백된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }

            When("핸들 변경 후 15일 쿨다운 기간 내에 재변경을 시도하면") {
                val m = memberService.createMember(
                    email = "u3_cd@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p3_cd",
                    providerUsername = "U3_CD",
                )
                // 1회 변경
                memberService.updateHandle(m.id, "handle_cd2")
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("400 BAD_REQUEST (member.handle.cooldown) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateHandle(m.id, "handle_cd3")
                    }
                    ex.status.value() shouldBe 400
                    ex.message shouldBe "member.handle.cooldown"
                }

                Then("핸들이 변경되지 않고 아웃박스가 추가 생성되지 않는다") {
                    memberRepository.find(m.id)?.handle shouldBe "handle_cd2"
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }

            When("핸들 변경 후 15일 쿨다운 기간이 지난 후 재변경하면") {
                val m = memberService.createMember(
                    email = "u3_pass@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p3_cd_pass",
                    providerUsername = "U3_PASS",
                )
                // 강제로 16일 전 타임스탬프 설정
                val memberEntity = memberRepository.find(m.id)!!
                val pastTimestamp = Instant.now().minus(16, ChronoUnit.DAYS)
                memberRepository.save(
                    Member(
                        id = memberEntity.id,
                        email = memberEntity.email,
                        handle = memberEntity.handle,
                        provider = memberEntity.provider,
                        providerId = memberEntity.providerId,
                        providerDisplayName = memberEntity.providerDisplayName,
                        audit = com.langlez.member.domain.MemberAudit(lastHandleUpdatedAt = pastTimestamp)
                    )
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                val updated = memberService.updateHandle(m.id, "pass_user2")

                Then("핸들이 정상적으로 변경된다") {
                    updated.handle shouldBe "pass_user2"
                }

                Then("아웃박스 레코드가 새로 추가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                }
            }

            When("존재하지 않는 회원 ID로 변경에 실패하면") {
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("404 NOT_FOUND (member.not-found) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateHandle(999999L, "newhandle")
                    }
                    ex.status.value() shouldBe 404
                    ex.message shouldBe "member.not-found"
                }

                Then("아웃박스가 생성되지 않는다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }
        }

        // =========================================================================
        // 2-1. 닉네임 수정 유스케이스 (V11 마이그레이션 + 다국어 저장 확인)
        // =========================================================================
        Given("닉네임 수정 시") {

            When("가입 직후에는") {
                val m = memberService.createMember(
                    email = "nickname_default@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p_nickname_default",
                    providerUsername = "NicknameDefault",
                )

                Then("닉네임이 null 이다 (백필하지 않는다)") {
                    memberRepository.find(m.id)?.nickname shouldBe null
                }
            }

            listOf(
                "한국어닉네임",
                "にほんごニックネーム",
                "中文昵称",
                "Кириллица",
                "Émile Zøe",
            ).forEach { nickname ->
                When("$nickname 로 수정하면") {
                    val m = memberService.createMember(
                        email = "nickname_${nickname.hashCode()}@test.com",
                        providerType = Member.Provider.GOOGLE,
                        providerId = "p_nickname_${nickname.hashCode()}",
                        providerUsername = "NicknameUser",
                    )

                    val updated = memberService.updatePersonalInfo(
                        id = m.id, gender = null, birthDay = null, country = null, nickname = nickname,
                    )

                    Then("DB 에 그대로 저장되고 다시 조회해도 같다") {
                        updated.nickname shouldBe nickname
                        memberRepository.find(m.id)?.nickname shouldBe nickname
                    }
                }
            }
        }

        // =========================================================================
        // 3. FCM 토큰 업데이트 유스케이스
        // =========================================================================
        Given("FCM 토큰 업데이트 시") {

            When("유효한 회원 ID와 FCM 토큰으로 업데이트하면") {
                val m = memberService.createMember(
                    email = "fcm@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p_fcm",
                    providerUsername = "FCM_User",
                )

                memberService.updateFcmToken(m.id, "sample_fcm_token_12345")

                Then("회원의 fcmToken 필드가 정상적으로 저장된다") {
                    val found = memberRepository.find(m.id)
                    found?.fcm shouldBe "sample_fcm_token_12345"
                }
            }

            When("존재하지 않는 회원 ID로 FCM 토큰 업데이트 시도 시") {
                Then("404 NOT_FOUND (member.not-found) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateFcmToken(999999L, "some_token")
                    }
                    ex.status.value() shouldBe 404
                    ex.message shouldBe "member.not-found"
                }
            }
        }

        // =========================================================================
        // 4. 회원 목록 조회 (커서 페이지네이션) 유스케이스
        // =========================================================================
        Given("회원 목록 조회 시") {

            When("회원 목록을 커서 페이징으로 조회하면") {
                val m1 = memberRepository.save(
                    Member(
                        email = "member1_page@test.com",
                        handle = "member1_page",
                        provider = Member.Provider.GOOGLE,
                        providerId = "gp1_page",
                        providerDisplayName = "member1_page"
                    )
                )
                val m2 = memberRepository.save(
                    Member(
                        email = "member2_page@test.com",
                        handle = "member2_page",
                        provider = Member.Provider.GOOGLE,
                        providerId = "gp2_page",
                        providerDisplayName = "member2_page"
                    )
                )

                Then("ID 내림차순으로 지정된 size 크기만큼 정상 결과가 반환된다") {
                    val page1 = memberRepository.findAll(1, null)
                    page1 shouldHaveSize 1
                    page1[0].id shouldBe m2.id

                    val page2 = memberRepository.findAll(1, m2.id)
                    page2 shouldHaveSize 1
                    page2[0].id shouldBe m1.id
                }
            }
        }

        // =========================================================================
        // 5. 온라인 상태 확인 유스케이스
        // =========================================================================
        Given("온라인 상태 확인 시") {

            When("존재하는 회원의 온라인 상태를 조회하면") {
                val m = memberService.createMember(
                    email = "u5@test.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "p5",
                    providerUsername = "U5",
                )
                every { memberOnlineTracker.checkOnline(m.id) } returns mapOf(m.id to true)

                Then("MemberOnlineTracker 트래커 상태값(true)을 반환한다") {
                    memberService.isOnline(m.handle) shouldBe true
                }
            }

            When("존재하지 않는 회원 handle로 조회하면") {
                Then("404 NOT_FOUND (member.not-found) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.isOnline("non_existing_ghost_user")
                    }
                    ex.status.value() shouldBe 404
                    ex.message shouldBe "member.not-found"
                }
            }
        }

        // =========================================================================
        // 6. 계정 상태 조회 포트 (JwtAuthenticationFilter 가 매 요청 부른다)
        // =========================================================================
        Given("계정 상태 조회 포트로 상태를 볼 때") {

            // 상태 변경 경로가 전부 repo.save 를 거쳐 캐시를 갱신하므로 낡은 상태가 남지 않는다.
            // 여기가 깨지면 정지시킨 회원이 캐시 TTL 동안 그대로 API 를 쓴다.
            // 회원을 블록마다 새로 만든다 — 하나를 돌려 쓰면 앞 블록이 남긴 상태가 뒤 블록의 전제가 된다.
            fun newMember(seq: String) = memberService.createMember(
                email = "u6-$seq@test.com",
                providerType = Member.Provider.GOOGLE,
                providerId = "p6-$seq",
                providerUsername = "U6",
            )

            When("가입 직후라면") {
                val m = newMember("created")

                Then("CREATED 로 보인다") {
                    memberStatusQuery.findStatus(m.id) shouldBe MemberQuery.Status.CREATED
                }
            }

            When("정지시키면") {
                val m = newMember("suspended")

                Then("곧바로 SUSPENDED 로 보인다") {
                    memberService.suspendMember(m.id, reason = "test")
                    memberStatusQuery.findStatus(m.id) shouldBe MemberQuery.Status.SUSPENDED
                }
            }

            When("정지를 풀면") {
                val m = newMember("unsuspended")
                memberService.suspendMember(m.id, reason = "test")

                Then("곧바로 ACTIVE 로 보인다") {
                    memberService.unsuspendMember(m.id)
                    memberStatusQuery.findStatus(m.id) shouldBe MemberQuery.Status.ACTIVE
                }
            }

            When("탈퇴시키면") {
                val m = newMember("withdrawn")

                Then("곧바로 WITHDRAWN 으로 보인다") {
                    memberService.withdrawMember(m.id)
                    memberStatusQuery.findStatus(m.id) shouldBe MemberQuery.Status.WITHDRAWN
                }
            }

            When("존재하지 않는 회원 id 라면") {
                Then("null 을 반환한다") {
                    memberStatusQuery.findStatus(-1L).shouldBeNull()
                }
            }
        }

        // =========================================================================
        // 6. 탈퇴 유스케이스
        // =========================================================================
        Given("회원 탈퇴 시") {

            When("정상적으로 탈퇴 처리하면") {
                val m = memberService.createMember(
                    email = "withdraw_test@example.com",
                    providerType = Member.Provider.GOOGLE,
                    providerId = "g789_test",
                    providerUsername = "WithdrawUser",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                memberService.withdrawMember(m.id)

                Then("회원 상태가 WITHDRAWN 으로 바뀐다") {
                    val found = memberRepository.find(m.id)
                    found?.status shouldBe Member.Status.WITHDRAWN
                }

                Then("이벤트 아웃박스에 member-withdrawn 레코드가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                    val outbox = outboxJpaRepository.findAll().last()
                    outbox.domain shouldBe "MEMBER"
                    outbox.topic shouldBe "member-withdrawn"
                    outbox.status shouldBe OutBox.Status.PENDING
                    outbox.key shouldBe m.id.toString()

                    val eventPayload = objectMapper.readValue(outbox.payload, MemberWithdrawnEvent::class.java)
                    eventPayload.id shouldBe m.id
                }
            }
        }
    }
}

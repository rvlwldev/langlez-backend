package com.langlez.member

import com.fasterxml.jackson.databind.ObjectMapper
import com.langlez.exception.LanglezException
import com.langlez.member.application.MemberOnlineTracker
import com.langlez.member.application.MemberService
import com.langlez.member.domain.Member
import com.langlez.member.application.MemberRepository
import com.langlez.core.event.member.MemberCreatedEvent
import com.langlez.core.event.member.MemberNicknameChangedEvent
import com.langlez.core.event.member.MemberUsernameChangedEvent
import com.langlez.member.domain.MemberProvider
import com.langlez.member.infrastructure.jpa.MemberOutBoxJpaRepository
import com.langlez.rdb.outbox.OutBoxStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.extensions.spring.SpringExtension
import io.kotest.matchers.collections.shouldHaveSize
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
import org.testcontainers.containers.PostgreSQLContainer
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
        "spring.jpa.hibernate.ddl-auto=create-drop",
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
    lateinit var outboxJpaRepository: MemberOutBoxJpaRepository

    @Autowired
    lateinit var memberOnlineTracker: MemberOnlineTracker

    @Autowired
    lateinit var objectMapper: ObjectMapper

    companion object {
        @JvmField
        val postgres: PostgreSQLContainer<*> = PostgreSQLContainer("postgres:16")
            .withDatabaseName("langlez_db")
            .withUsername("admin")
            .withPassword("admin")
            .also { it.start() }

        @DynamicPropertySource
        @JvmStatic
        fun configureProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url") { postgres.jdbcUrl }
            registry.add("spring.datasource.username") { postgres.username }
            registry.add("spring.datasource.password") { postgres.password }
        }
    }

    init {
        // =========================================================================
        // 1. 회원 가입 (생성) 유스케이스
        // =========================================================================
        Given("회원 가입 시") {

            When("정상적인 정보로 회원 가입에 성공하면") {
                val beforeMemberCount = memberRepository.countAll()
                val beforeOutboxCount = outboxJpaRepository.count()

                val result = memberService.createMember(
                    email = "create_test@example.com",
                    nickname = "Test User",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "g123_test",
                    providerUsername = "Test User",
                )

                Then("DB에 회원 정보가 저장된다") {
                    result.id shouldNotBe null
                    val found = memberRepository.findById(result.id)
                    found shouldNotBe null
                    found?.email shouldBe "create_test@example.com"
                    found?.username shouldBe result.username
                }

                Then("멤버 총 카운트가 1 증가한다") {
                    memberRepository.countAll() shouldBe beforeMemberCount + 1
                }

                Then("이벤트 아웃박스(member_event_outbox)에 member-created 레코드가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                    val createdOutbox = outboxJpaRepository.findAll().last()
                    createdOutbox.domain shouldBe "MEMBER"
                    createdOutbox.topic shouldBe "member-created"
                    createdOutbox.status shouldBe OutBoxStatus.READY
                    createdOutbox.key shouldBe result.id.toString()

                    val eventPayload = objectMapper.readValue(createdOutbox.payload, MemberCreatedEvent::class.java)
                    eventPayload.id shouldBe result.id
                    eventPayload.email shouldBe "create_test@example.com"
                }
            }

            When("이메일/유저네임 중복 등 DB 제약 위반으로 회원 가입에 실패하면") {
                val beforeMemberCount = memberRepository.countAll()
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("DB 제약위반 예외(DataIntegrityViolationException)가 발생한다") {
                    shouldThrow<DataIntegrityViolationException> {
                        memberService.createMember(
                            email = "create_test@example.com",
                            nickname = "Test User",
                            providerType = MemberProvider.GOOGLE,
                            providerId = "g123_test",
                            providerUsername = "Test User",
                        )
                    }
                }

                Then("멤버 카운트가 증가하지 않는다") {
                    memberRepository.countAll() shouldBe beforeMemberCount
                }

                Then("이벤트 아웃박스 레코드가 생성되지 않고 롤백된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }
        }

        // =========================================================================
        // 2. 유저네임 변경 유스케이스
        // =========================================================================
        Given("유저네임 변경 시") {

            When("유효한 유저네임으로 변경에 성공하면") {
                val m = memberService.createMember(
                    email = "user2@example.com",
                    nickname = "User2",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "g456_test",
                    providerUsername = "User2",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                val updated = memberService.updateUsername(m.id, "user2_after")

                Then("유저네임이 변경된다") {
                    updated.username shouldBe "user2_after"
                    val found = memberRepository.findById(m.id)
                    found?.username shouldBe "user2_after"
                }

                Then("lastUsernameUpdatedAt 타임스탬프가 업데이트된다") {
                    val found = memberRepository.findById(m.id)
                    found?.lastUsernameUpdatedAt shouldNotBe null
                }

                Then("이벤트 아웃박스에 member-username-changed 레코드가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                    val outbox = outboxJpaRepository.findAll().last()
                    outbox.topic shouldBe "member-username-changed"
                    outbox.key shouldBe m.id.toString()

                    val eventPayload = objectMapper.readValue(outbox.payload, MemberUsernameChangedEvent::class.java)
                    eventPayload.id shouldBe m.id
                    eventPayload.oldUsername shouldBe m.username
                    eventPayload.newUsername shouldBe "user2_after"
                }
            }

            When("이미 사용 중인 유저네임으로 변경에 실패하면") {
                val m1 = memberService.createMember(
                    email = "u1@test.com",
                    nickname = "U1",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p1",
                    providerUsername = "U1",
                )
                val m2 = memberService.createMember(
                    email = "u2@test.com",
                    nickname = "U2",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p2",
                    providerUsername = "U2",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("409 CONFLICT (member.username.duplicated) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateUsername(m2.id, m1.username)
                    }
                    ex.status.value() shouldBe 409
                    ex.message shouldBe "member.username.duplicated"
                }

                Then("회원 유저네임이 변경되지 않고 이전 상태를 유지한다") {
                    memberRepository.findById(m2.id)?.username shouldBe m2.username
                }

                Then("이벤트 아웃박스 생성이 롤백된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }

            When("형식이 유효하지 않은 유저네임(2자 미만)으로 변경에 실패하면") {
                val m = memberService.createMember(
                    email = "u3@test.com",
                    nickname = "U3",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p3",
                    providerUsername = "U3",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("400 BAD_REQUEST (member.username.invalid) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateUsername(m.id, "a")
                    }
                    ex.status.value() shouldBe 400
                    ex.message shouldBe "member.username.invalid"
                }

                Then("아웃박스가 생성되지 않고 롤백된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }

            When("유저네임 변경 후 15일 쿨다운 기간 내에 재변경을 시도하면") {
                val m = memberService.createMember(
                    email = "u3_cd@test.com",
                    nickname = "U3_CD",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p3_cd",
                    providerUsername = "U3_CD",
                )
                // 1회 변경
                memberService.updateUsername(m.id, "username_cd2")
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("400 BAD_REQUEST (member.username.cooldown) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateUsername(m.id, "username_cd3")
                    }
                    ex.status.value() shouldBe 400
                    ex.message shouldBe "member.username.cooldown"
                }

                Then("유저네임이 변경되지 않고 아웃박스가 추가 생성되지 않는다") {
                    memberRepository.findById(m.id)?.username shouldBe "username_cd2"
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }

            When("유저네임 변경 후 15일 쿨다운 기간이 지난 후 재변경하면") {
                val m = memberService.createMember(
                    email = "u3_pass@test.com",
                    nickname = "U3_PASS",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p3_cd_pass",
                    providerUsername = "U3_PASS",
                )
                // 강제로 16일 전 타임스탬프 설정
                val memberEntity = memberRepository.findById(m.id)!!
                val pastTimestamp = Instant.now().minus(16, ChronoUnit.DAYS)
                memberRepository.save(Member(
                    id = memberEntity.id,
                    email = memberEntity.email,
                    username = memberEntity.username,
                    nickname = memberEntity.nickname,
                    provider = memberEntity.provider,
                    providerId = memberEntity.providerId,
                    providerDisplayName = memberEntity.providerDisplayName,
                    lastUsernameUpdatedAt = pastTimestamp
                ))
                val beforeOutboxCount = outboxJpaRepository.count()

                val updated = memberService.updateUsername(m.id, "pass_user2")

                Then("유저네임이 정상적으로 변경된다") {
                    updated.username shouldBe "pass_user2"
                }

                Then("아웃박스 레코드가 새로 추가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                }
            }

            When("존재하지 않는 회원 ID로 변경에 실패하면") {
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("404 NOT_FOUND (member.not-found) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateUsername(999999L, "newusername")
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
        // 3. 닉네임 변경 유스케이스
        // =========================================================================
        Given("닉네임 변경 시") {

            When("유효한 닉네임으로 변경에 성공하면") {
                val m = memberService.createMember(
                    email = "u4@test.com",
                    nickname = "OldNick",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p4",
                    providerUsername = "U4",
                )
                val beforeOutboxCount = outboxJpaRepository.count()

                val updated = memberService.updateNickname(m.id, "NewNick")

                Then("닉네임이 변경된다") {
                    updated.nickname shouldBe "NewNick"
                    val found = memberRepository.findById(m.id)
                    found?.nickname shouldBe "NewNick"
                }

                Then("lastNicknameUpdatedAt 타임스탬프가 업데이트된다") {
                    val found = memberRepository.findById(m.id)
                    found?.lastNicknameUpdatedAt shouldNotBe null
                }

                Then("이벤트 아웃박스에 member-nickname-changed 레코드가 저장된다") {
                    outboxJpaRepository.count() shouldBe beforeOutboxCount + 1
                    val outbox = outboxJpaRepository.findAll().last()
                    outbox.topic shouldBe "member-nickname-changed"

                    val eventPayload = objectMapper.readValue(outbox.payload, MemberNicknameChangedEvent::class.java)
                    eventPayload.id shouldBe m.id
                    eventPayload.newNickname shouldBe "NewNick"
                }
            }

            When("닉네임 변경 후 15일 쿨다운 기간 내에 재변경을 시도하면") {
                val m = memberService.createMember(
                    email = "u4_cd@test.com",
                    nickname = "NickCD1",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p4_cd",
                    providerUsername = "U4_CD",
                )
                memberService.updateNickname(m.id, "NickCD2")
                val beforeOutboxCount = outboxJpaRepository.count()

                Then("400 BAD_REQUEST (member.nickname.cooldown) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.updateNickname(m.id, "NickCD3")
                    }
                    ex.status.value() shouldBe 400
                    ex.message shouldBe "member.nickname.cooldown"
                }

                Then("닉네임이 변경되지 않고 아웃박스가 생성되지 않는다") {
                    memberRepository.findById(m.id)?.nickname shouldBe "NickCD2"
                    outboxJpaRepository.count() shouldBe beforeOutboxCount
                }
            }
        }

        // =========================================================================
        // 4. FCM 토큰 업데이트 유스케이스
        // =========================================================================
        Given("FCM 토큰 업데이트 시") {

            When("유효한 회원 ID와 FCM 토큰으로 업데이트하면") {
                val m = memberService.createMember(
                    email = "fcm@test.com",
                    nickname = "FCM_User",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p_fcm",
                    providerUsername = "FCM_User",
                )

                memberService.updateFcmToken(m.id, "sample_fcm_token_12345")

                Then("회원의 fcmToken 필드가 정상적으로 저장된다") {
                    val found = memberRepository.findById(m.id)
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
        // 5. 회원 목록 조회 (커서 페이지네이션) 유스케이스
        // =========================================================================
        Given("회원 목록 조회 시") {

            When("회원 목록을 커서 페이징으로 조회하면") {
                val m1 = memberRepository.save(
                    Member(
                        email = "member1_page@test.com",
                        username = "member1_page",
                        nickname = "member1_page",
                        provider = MemberProvider.GOOGLE,
                        providerId = "gp1_page",
                        providerDisplayName = "member1_page"
                    )
                )
                val m2 = memberRepository.save(
                    Member(
                        email = "member2_page@test.com",
                        username = "member2_page",
                        nickname = "member2_page",
                        provider = MemberProvider.GOOGLE,
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
        // 6. 온라인 상태 확인 유스케이스
        // =========================================================================
        Given("온라인 상태 확인 시") {

            When("존재하는 회원의 온라인 상태를 조회하면") {
                val m = memberService.createMember(
                    email = "u5@test.com",
                    nickname = "U5",
                    providerType = MemberProvider.GOOGLE,
                    providerId = "p5",
                    providerUsername = "U5",
                )
                every { memberOnlineTracker.checkStatus(m.username) } returns true

                Then("MemberOnlineTracker 트래커 상태값(true)을 반환한다") {
                    memberService.isOnline(m.username) shouldBe true
                }
            }

            When("존재하지 않는 회원 username으로 조회하면") {
                Then("404 NOT_FOUND (member.not-found) 예외가 발생한다") {
                    val ex = shouldThrow<LanglezException> {
                        memberService.isOnline("non_existing_ghost_user")
                    }
                    ex.status.value() shouldBe 404
                    ex.message shouldBe "member.not-found"
                }
            }
        }
    }
}

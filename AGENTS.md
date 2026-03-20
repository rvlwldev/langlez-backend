# Langlez Backend - Project Conventions

## Overview

언어 학습 소셜 네트워킹 플랫폼 백엔드. Spring Boot 3.x + Kotlin + Java 21 (Virtual Threads).
Modular Monolith 구조로 마이크로서비스 전환을 염두에 둔 설계.

## Module Structure

```
langlez-backend/
├── app/api                  # Spring Boot 진입점
├── core/                    # 공유 인터페이스 (OutBoxEventPublisher)
├── common/
│   ├── exception/           # LanglezException, GlobalRestControllerAdvice
│   ├── jackson/             # JSON 직렬화 설정
│   ├── observability/       # P6Spy, Prometheus, 쿼리 로거
│   ├── security/            # JWT(JwtParser), OAuth2, 필터, 어노테이션
│   └── web/                 # Swagger, 글로벌 에러 핸들링, WebConfiguration
├── infra/
│   ├── files/               # FileStorage 인터페이스 (S3FileStorage, LocalFileStorage)
│   ├── kafka/               # KafkaConfiguration (Producer/Consumer)
│   ├── mongo/               # MongoDB 트랜잭션, 감사
│   ├── mysql/               # MySQLConfiguration, JPAQueryFactory
│   └── redis/               # Redisson, ResilientCache, @DistributedLock, RedisLockService
└── module/
    ├── auth/                # OAuth2 (Google, Apple), 토큰 갱신
    ├── member/              # 회원 CRUD, 캐싱, 이벤트 발행
    ├── outbox/              # OutBox 패턴 (5초 폴링, Kafka 발행, 아카이빙)
    ├── profile/             # 프로필 + ProfileImage 관리
    ├── profile_backup/      # 프로필 백업
    └── relationship/        # Follow / Block
```

## Architecture Rules

### Layered Architecture (모듈 내부)
```
api → application → domain ← infrastructure
```
- **Domain**: 순수 비즈니스 로직. Spring 프레임워크 import 금지 (JPA/Jakarta persistence 제외)
- **Application**: @Service, @Transactional. 도메인 조합 + 이벤트 발행
- **Infrastructure**: Repository 구현체, 캐싱 데코레이터. JpaRepository 위임
- **API**: @RestController, DTO. 비즈니스 로직 없음

### Cross-module Communication
- 모듈 간 직접 의존은 최소화
- 이벤트 기반 통신: OutBoxEventPublisher → Kafka
- 공유 인터페이스는 core/ 모듈에

### Dependency Direction (build.gradle.kts)
- `infra/mysql`이 `api(springboot.jpa)` 노출 → 모듈에서 중복 선언 금지
- `infra/kafka`가 `api(spring.kafka)` 노출 → 모듈에서 중복 선언 금지
- `common/web`이 `api(springboot.web, validation, swagger)` 노출

## Coding Conventions

### Entity
```kotlin
@Entity
@Table(name = "snake_case_plural", uniqueConstraints = [...], indexes = [...])
class EntityName(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    // 필드들...
    @CreatedDate var createdAt: Instant = Instant.now(),
    @LastModifiedDate var updatedAt: Instant = Instant.now(),
    var deletedAt: Instant? = null,          // soft delete
    @Version var version: Long = 0            // optimistic locking
) {
    fun businessMethod() { ... }              // 도메인 메서드
    enum class Status { ... }                 // 중첩 enum
    companion object { ... }                  // 팩토리/유틸
}
```

### Repository Pattern
```kotlin
// Domain layer - 순수 인터페이스
interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: Long): Member?
}

// Infrastructure layer - 구현체 (JPA 위임 + 캐싱)
@Repository
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,
) : MemberRepository {
    @Cacheable(cacheNames = ["member"], key = "#id")
    override fun findById(id: Long): Member? = jpa.findByIdOrNull(id)
}

interface MemberJpaRepository : JpaRepository<Member, Long> { ... }
```

### Command / Event (CQRS)
```kotlin
sealed class MemberCommand {
    data class Create(val email: String, val nickname: String) : MemberCommand()
}

sealed interface MemberEvent {
    data class Created(val id: Long, val email: String) : MemberEvent
}
```

### API 식별자 규칙
- **엔티티 ID (Long) 절대 노출 금지**: 클라이언트에게 요청으로 받지도, 응답으로 반환하지도 않는다.
- **사용자 식별**: `username` (String)을 사용. URL 경로에서는 `@{username}` 형식 사용.
  - 예: `GET /api/v1/members/@john_doe`, `POST /api/v1/relationship/follow/@jane123`
- **인증된 사용자 (주체)**: `@MemberID` 어노테이션으로 JWT 토큰에서 추출한 Long ID를 서버 내부에서만 사용. 클라이언트는 자신의 ID를 알 수 없다.
- **대상 사용자 (타겟)**: URL 경로의 `@{username}`으로 받아서, Controller에서 `MemberRepository.findByUsername()`으로 내부 ID를 resolve.
- **서비스 레이어**: 내부적으로 Long ID로 동작. Controller가 username → ID 변환을 담당.
- **응답 DTO**: `id` 필드 포함 금지. `username`, `nickname` 등 공개 가능한 정보만 반환.
- **커서 기반 페이지네이션**: 커서 값은 관계 엔티티(Follow/Block)의 ID를 사용 (member ID가 아님). 이는 순서를 보장하는 불투명한 값으로, 회원 식별에 사용 불가.

### 파라미터 네이밍 규칙 (Relationship)
| 역할 | Controller 파라미터 | Service 파라미터 |
|------|---------------------|-----------------|
| 팔로우 주체 (토큰) | `followerId: Long` (@MemberID) | `followerId: Long` |
| 팔로우 대상 (경로) | `followingUsername: String` | `followingId: Long` |
| 차단 주체 (토큰) | `blockerId: Long` (@MemberID) | `blockerId: Long` |
| 차단 대상 (경로) | `blockedUsername: String` | `blockedId: Long` |

### Controller / Service 책임 분리
- **Controller (얇게)**: 요청을 DTO로 받고, 알맞는 Service 또는 Repository를 호출하고, 결과를 응답 DTO로 변환하여 반환. 비즈니스 로직을 포함하지 않는다.
  - Controller가 Repository를 직접 사용하는 경우는 **단순 데이터 조회**로 제한 (예: `findById` → NOT_FOUND 체크 → 반환).
  - 검증, 상태 변경, 복합 조회 등의 비즈니스 로직은 반드시 Service에 위치한다.
  - 메서드명은 HTTP 메서드를 반영한다: `getMe()`, `patchUsername()`, `patchNickname()`, `deleteMe()` 등.
- **Service (두껍게)**: 비즈니스 흐름을 제어한다. 도메인 객체의 메서드를 호출하여 검증/상태 변경을 수행하고, Repository를 통해 영속화한다.
  - Service는 흐름만 제어하고, 검증 로직은 도메인 객체(Entity) 내부에 구현한다 (객체지향적 설계).
  - 예: `Member.canChangeUsername()` → Service에서 호출 → 실패 시 예외 발생.

### 도메인 메서드 패턴 (Entity 내부 비즈니스 로직)
```kotlin
// Entity에서 변경 가능 여부를 판단하는 메서드 제공
fun canChangeUsername(now: Instant = Instant.now()): Boolean = ...
fun changeUsername(newUsername: String, now: Instant = Instant.now()) { ... }

// Service에서 도메인 메서드를 호출하여 흐름 제어
fun updateUsername(memberId: Long, newUsername: String): Member {
    val member = repo.findById(memberId) ?: throw ...
    if (!member.canChangeUsername()) throw ...
    member.changeUsername(newUsername)
    return repo.save(member)
}
```

### Username/Nickname 변경 쿨다운
- 각각 15일 간격으로만 변경 가능.
- `Member` 엔티티의 `lastUsernameUpdatedAt`, `lastNicknameUpdatedAt` 필드로 추적.
- Username, Nickname 변경은 별도 엔드포인트로 분리: `PATCH /me/username`, `PATCH /me/nickname`.
- Username 변경 시 Service에서 유효성 검증 + 중복 확인 + 쿨다운 확인을 수행.

### Controller + DTO
```kotlin
@RestController
@RequestMapping("/api/v1/members")
@Tag(name = "Member")
class MemberController(
    private val service: MemberService,
    private val repo: MemberRepository   // 단순 조회 전용
) {
    @GetMapping("/me")
    fun getMe(@MemberID memberId: Long): MemberResponse.Me { ... }

    @PatchMapping("/me/username")
    fun patchUsername(@MemberID memberId: Long, @RequestBody @Valid request: MemberRequest.UpdateUsername): MemberResponse.Me { ... }
}

class MemberRequest {
    data class UpdateUsername(
        @field:NotBlank @field:Size(min = 3, max = 20) val username: String,
    )
    data class UpdateNickname(
        @field:NotBlank @field:Size(min = 1, max = 20) val nickname: String,
    )
}
```

### Style
- No coroutines (Virtual Threads 사용)
- Kotlin idiomatic: `let`, `apply`, `with`, data class, sealed class
- 로깅: `kotlin-logging` (io.github.oshai)
- 매직 넘버 → 상수 추출

## Configuration Management

### Centralized Configuration
- **설정 통합**: 모든 모듈의 `application.yml`을 제거하고 `app/api/src/main/resources/`에서 통합 관리합니다.
- **사용 파일**:
  - `application.yml`: 로컬 개발용 (기본 활성 프로파일: `local`). `docker/` 폴더의 인프라 설정과 동기화됨.
  - `application-production.yml`: 운영 환경용. 민감한 정보는 환경 변수(`${...}`)를 통해 주입받음.
- **모듈별 설정 금지**: 하위 모듈(`module/*`, `infra/*`, `common/*`)에 `application.yml`을 생성하지 않습니다. 필요한 기본값은 코드(`@Value`, `@ConfigurationProperties`)에서 정의합니다.

## Build Commands

```bash
./gradlew build                          # 전체 빌드
./gradlew :app:api:bootRun              # 앱 실행
./gradlew :module:<name>:test           # 모듈별 테스트
./gradlew :module:<name>:build         # 모듈별 빌드
```

## Test Conventions

### Framework
- Kotest 5.9.0 (Runner: kotest-runner-junit5-jvm, Assertions: kotest-assertions-core-jvm)
- MockK 1.13.10
- Kotest Spring Extension 1.1.3
- TestContainers 1.20.4 (MySQL, Redis, MongoDB, Kafka)
- TestRestTemplate (spring-boot-starter-test 내장, E2E 실제 HTTP 요청)

### Test Pyramid
| 계층 | 비율 | Spec | Spring Context | 외부 의존 |
|------|------|------|---------------|----------|
| Domain Unit | 70% | BehaviorSpec | 없음 | MockK |
| Integration | 20% | DescribeSpec | @SpringBootTest / Slice | TestContainers |
| E2E | 10% | DescribeSpec | @SpringBootTest(RANDOM_PORT) | TestContainers + TestRestTemplate |

### Naming
- 테스트 클래스: `<ClassName>Test.kt` (미러 패키지 `src/test/kotlin/`)
- 테스트 설명: 한국어
  - BehaviorSpec: `Given("회원이 존재할 때")` / `When("팔로우하면")` / `Then("관계가 생성된다")`
  - DescribeSpec: `describe("MemberService")` / `context("유효한 요청 시")` / `it("회원이 생성되어야 한다")`

### Unit Test Rules (Domain) — 70%
- BehaviorSpec 사용, Spring Context 로딩 금지
- `mockk<>()` + `every {} returns` + `verify {}`
- `@MockBean` 사용 금지 (MockK 직접 사용)
- Happy / Sad / Edge 시나리오 커버

#### Example: Entity Domain Test
```kotlin
// module/member/src/test/kotlin/com/langlez/member/domain/MemberTest.kt
class MemberTest : BehaviorSpec({

    Given("일반 회원이 주어졌을 때") {
        val member = Member(
            email = "test@langlez.com",
            nickname = "tester",
            provider = MemberProvider("google-123", MemberProvider.Type.GOOGLE)
        )

        When("로그인하면") {
            member.login()

            Then("마지막 로그인 시간이 기록되어야 한다") {
                member.lastLoggedInAt.shouldNotBeNull()
            }
        }

        When("프리미엄으로 업그레이드하면") {
            member.upgradeToPremium()

            Then("역할이 PREMIUM이어야 한다") {
                member.role shouldBe Member.Role.PREMIUM
            }
        }

        When("삭제하면") {
            member.delete()

            Then("deletedAt이 설정되어야 한다") {
                member.deletedAt.shouldNotBeNull()
            }
        }
    }

    Given("유효하지 않은 username이 주어졌을 때") {

        When("2자 이하인 경우") {
            Then("유효하지 않아야 한다") {
                Member.isValidUsername("ab") shouldBe false
            }
        }

        When("특수문자가 포함된 경우") {
            Then("유효하지 않아야 한다") {
                Member.isValidUsername("user@name") shouldBe false
            }
        }
    }

    Given("유효한 username이 주어졌을 때") {
        When("영문, 숫자, 언더스코어 3~20자인 경우") {
            Then("유효해야 한다") {
                Member.isValidUsername("valid_user_123") shouldBe true
            }
        }
    }
})
```

#### Example: Service Unit Test
```kotlin
// module/member/src/test/kotlin/com/langlez/member/application/MemberServiceTest.kt
class MemberServiceTest : BehaviorSpec({

    val memberRepository = mockk<MemberRepository>()
    val outBoxPublisher = mockk<OutBoxEventPublisher>(relaxed = true)
    val service = MemberService(memberRepository, outBoxPublisher)

    Given("유효한 회원 생성 요청이 주어졌을 때") {
        val providerCmd = MemberCommand.Provider("google-123", MemberProvider.Type.GOOGLE, "tester")
        val createCmd = MemberCommand.Create(email = "test@langlez.com", nickname = "tester")

        val savedMember = Member(
            id = 1L,
            email = createCmd.email,
            nickname = createCmd.nickname,
            provider = MemberProvider(providerCmd.id, providerCmd.type)
        )
        every { memberRepository.save(any()) } returns savedMember

        When("회원을 생성하면") {
            val result = service.createMember(providerCmd, createCmd)

            Then("회원이 저장되어야 한다") {
                result.email shouldBe "test@langlez.com"
                result.nickname shouldBe "tester"
                verify(exactly = 1) { memberRepository.save(any()) }
            }

            Then("OutBox 이벤트가 발행되어야 한다") {
                verify {
                    outBoxPublisher.publish(
                        eq("MEMBER"),
                        any(),
                        eq("member-created"),
                        any<MemberEvent.Created>()
                    )
                }
            }

            Then("로그인 시간이 기록되어야 한다") {
                result.lastLoggedInAt.shouldNotBeNull()
            }
        }
    }
})
```

#### Example: Relationship Service Test (Happy + Sad + Edge)
```kotlin
// module/relationship/src/test/kotlin/com/langlez/relationship/application/RelationshipServiceTest.kt
class RelationshipServiceTest : BehaviorSpec({

    val repo = mockk<RelationshipRepository>()
    val publisher = mockk<OutBoxEventPublisher>(relaxed = true)
    val service = RelationshipService(repo, publisher)

    // --- Happy Path ---
    Given("서로 차단하지 않은 두 회원이 주어졌을 때") {
        every { repo.findBlock(any(), any()) } returns null
        every { repo.saveFollow(any()) } returns Follow(id = 1L, followerId = 1L, followingId = 2L)

        When("팔로우하면") {
            service.follow(followerId = 1L, followingId = 2L)

            Then("팔로우 관계가 저장되어야 한다") {
                verify(exactly = 1) { repo.saveFollow(any()) }
            }

            Then("팔로우 이벤트가 발행되어야 한다") {
                verify { publisher.publish(eq("RELATIONSHIP"), any(), eq("MEMBER_FOLLOW"), any()) }
            }
        }
    }

    // --- Sad Path ---
    Given("자기 자신을 팔로우하려는 경우") {
        When("팔로우하면") {
            Then("BAD_REQUEST 예외가 발생해야 한다") {
                val exception = shouldThrow<LanglezException> {
                    service.follow(followerId = 1L, followingId = 1L)
                }
                exception.status shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }

    Given("상대방에게 차단된 상태일 때") {
        every { repo.findBlock(2L, 1L) } returns Block(id = 1L, blockerId = 2L, blockedId = 1L)

        When("팔로우하면") {
            Then("FORBIDDEN 예외가 발생해야 한다") {
                shouldThrow<LanglezException> {
                    service.follow(followerId = 1L, followingId = 2L)
                }.status shouldBe HttpStatus.FORBIDDEN
            }
        }
    }

    // --- Edge Path ---
    Given("팔로우 관계가 존재하지 않을 때") {
        every { repo.findFollow(1L, 2L) } returns null

        When("언팔로우하면") {
            service.unfollow(followerId = 1L, followingId = 2L)

            Then("아무 동작도 하지 않아야 한다") {
                verify(exactly = 0) { repo.deleteFollow(any(), any()) }
                verify(exactly = 0) { publisher.publish(any(), any(), any(), any()) }
            }
        }
    }

    // --- Block Flow ---
    Given("서로 팔로우 중인 두 회원이 주어졌을 때") {
        every { repo.saveBlock(any()) } returns Block(id = 1L, blockerId = 1L, blockedId = 2L)
        every { repo.deleteFollow(any(), any()) } returns Unit

        When("차단하면") {
            service.block(blockerId = 1L, blockedId = 2L)

            Then("양방향 팔로우가 모두 삭제되어야 한다") {
                verify(exactly = 1) { repo.deleteFollow(1L, 2L) }
                verify(exactly = 1) { repo.deleteFollow(2L, 1L) }
            }

            Then("차단 관계가 저장되어야 한다") {
                verify(exactly = 1) { repo.saveBlock(any()) }
            }

            Then("차단 이벤트가 발행되어야 한다") {
                verify { publisher.publish(eq("RELATIONSHIP"), any(), eq("MEMBER_BLOCK"), any()) }
            }
        }
    }
})
```

### Integration Test Rules — 20%
- @SpringBootTest 또는 Slice (@DataJpaTest 등) + TestContainers
- @Transactional로 롤백 또는 afterTest에서 cleanup
- 캐시 동작 검증 (hit/miss/eviction)

#### Example: Repository Integration Test
```kotlin
// module/member/src/test/kotlin/com/langlez/member/infrastructure/MemberRepositoryImplTest.kt
@SpringBootTest
@Transactional
class MemberRepositoryImplTest(
    private val repository: MemberRepository,
    private val cacheManager: CacheManager,
) : DescribeSpec({

    extensions(SpringExtension)

    describe("MemberRepository") {

        context("회원 저장 후 조회 시") {
            it("ID로 조회할 수 있어야 한다") {
                val member = repository.save(
                    Member(
                        email = "test@langlez.com",
                        nickname = "tester",
                        provider = MemberProvider("google-123", MemberProvider.Type.GOOGLE)
                    )
                )

                val found = repository.findById(member.id)

                found.shouldNotBeNull()
                found.email shouldBe "test@langlez.com"
            }

            it("이메일로 조회할 수 있어야 한다") {
                repository.save(
                    Member(
                        email = "find-me@langlez.com",
                        nickname = "finder",
                        provider = MemberProvider("apple-456", MemberProvider.Type.APPLE)
                    )
                )

                val found = repository.findByEmail("find-me@langlez.com")

                found.shouldNotBeNull()
                found.nickname shouldBe "finder"
            }

            it("존재하지 않는 이메일은 null을 반환해야 한다") {
                repository.findByEmail("nonexistent@test.com").shouldBeNull()
            }
        }

        context("캐시 동작") {
            it("조회 후 캐시에 저장되어야 한다") {
                val member = repository.save(
                    Member(
                        email = "cache@langlez.com",
                        nickname = "cached",
                        provider = MemberProvider("g-789", MemberProvider.Type.GOOGLE)
                    )
                )

                // 1차 조회 - DB
                repository.findById(member.id)
                // 2차 조회 - 캐시
                repository.findById(member.id)

                val cache = cacheManager.getCache("member")
                cache?.get(member.id).shouldNotBeNull()
            }

            it("저장 시 캐시가 무효화되어야 한다") {
                val member = repository.save(
                    Member(
                        email = "evict@langlez.com",
                        nickname = "before",
                        provider = MemberProvider("g-evict", MemberProvider.Type.GOOGLE)
                    )
                )

                // 캐시 적재
                repository.findById(member.id)

                // 수정 후 저장 → 캐시 evict
                member.nickname = "after"
                repository.save(member)

                val cache = cacheManager.getCache("member")
                cache?.get(member.id).shouldBeNull()
            }
        }

        context("벌크 조회") {
            it("여러 ID로 조회할 수 있어야 한다") {
                val m1 = repository.save(Member(email = "bulk1@test.com", nickname = "b1", provider = MemberProvider("b1", MemberProvider.Type.GOOGLE)))
                val m2 = repository.save(Member(email = "bulk2@test.com", nickname = "b2", provider = MemberProvider("b2", MemberProvider.Type.GOOGLE)))

                val results = repository.findByIds(listOf(m1.id, m2.id))

                results shouldHaveSize 2
                results.map { it.email } shouldContainAll listOf("bulk1@test.com", "bulk2@test.com")
            }
        }
    }
})
```

### E2E Test Rules — 10%
- @SpringBootTest(webEnvironment = RANDOM_PORT) + TestContainers
- TestRestTemplate (spring-boot-starter-test 내장, 실제 HTTP 요청)
- JWT 인증이 필요한 API는 토큰 생성 후 HttpHeaders에 포함
- 변수명: `rest` 사용

#### Example: Auth E2E Test
```kotlin
// module/auth/src/test/kotlin/com/langlez/auth/e2e/AuthE2ETest.kt
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthE2ETest(
    private val rest: TestRestTemplate,
    private val jwtParser: JwtParser,
    private val redis: StringRedisTemplate,
) : DescribeSpec({

    extensions(SpringExtension)

    describe("POST /api/v1/auth/refresh") {

        context("유효한 리프레시 토큰으로 요청 시") {
            it("새 토큰 쌍을 반환해야 한다") {
                val memberId = 1L
                val refreshToken = jwtParser.createRefreshToken(memberId)
                redis.opsForValue().set("refresh:$memberId", refreshToken)

                val response = rest.postForEntity(
                    "/api/v1/auth/refresh",
                    AuthRequest.RefreshToken(refreshToken),
                    AuthResponse.NewTokens::class.java
                )

                response.statusCode shouldBe HttpStatus.OK
                response.body?.accessToken.shouldNotBeNull()
                response.body?.refreshToken.shouldNotBeNull()
            }
        }

        context("빈 리프레시 토큰으로 요청 시") {
            it("400을 반환해야 한다") {
                val response = rest.postForEntity(
                    "/api/v1/auth/refresh",
                    AuthRequest.RefreshToken(""),
                    Any::class.java
                )

                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }

        context("만료된 리프레시 토큰으로 요청 시") {
            it("401을 반환해야 한다") {
                val response = rest.postForEntity(
                    "/api/v1/auth/refresh",
                    AuthRequest.RefreshToken("expired.invalid.token"),
                    Any::class.java
                )

                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }
    }
})
```

#### Example: 인증이 필요한 API E2E Test
```kotlin
// module/relationship/src/test/kotlin/com/langlez/relationship/e2e/RelationshipE2ETest.kt
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RelationshipE2ETest(
    private val rest: TestRestTemplate,
    private val jwtParser: JwtParser,
) : DescribeSpec({

    extensions(SpringExtension)

    // 인증 헤더 생성 헬퍼
    fun bearerHeaders(memberId: Long): HttpHeaders = HttpHeaders().apply {
        setBearerAuth(jwtParser.createAccessToken(memberId))
    }

    describe("POST /api/v1/relationship/follow/@{followingId}") {

        context("인증된 사용자가 다른 회원을 팔로우할 때") {
            it("200을 반환해야 한다") {
                val response = rest.exchange(
                    "/api/v1/relationship/follow/@2",
                    HttpMethod.POST,
                    HttpEntity<Unit>(bearerHeaders(memberId = 1L)),
                    Unit::class.java
                )

                response.statusCode shouldBe HttpStatus.OK
            }
        }

        context("인증 없이 팔로우할 때") {
            it("401을 반환해야 한다") {
                val response = rest.postForEntity(
                    "/api/v1/relationship/follow/@2",
                    null,
                    Any::class.java
                )

                response.statusCode shouldBe HttpStatus.UNAUTHORIZED
            }
        }

        context("자기 자신을 팔로우할 때") {
            it("400을 반환해야 한다") {
                val response = rest.exchange(
                    "/api/v1/relationship/follow/@1",
                    HttpMethod.POST,
                    HttpEntity<Unit>(bearerHeaders(memberId = 1L)),
                    Any::class.java
                )

                response.statusCode shouldBe HttpStatus.BAD_REQUEST
            }
        }
    }
})
```

### Anti-patterns
- `Thread.sleep()` 금지 → Kotest `eventually {}` / `continually {}`
- private 메서드 직접 테스트 금지
- 테스트 간 상태 공유 금지
- `any()` 남발 금지 → 핵심 인자는 구체적 값으로 매칭 (`eq()`)
- 한 `Then` 블록에 관련 없는 다수 검증 금지 → 관심사별 `Then` 분리

### New Module build.gradle.kts Template
```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
}

dependencies {
    implementation(project(":core"))
    implementation(project(":common:web"))        // springboot.web, validation, swagger 포함
    implementation(project(":common:security"))
    implementation(project(":common:observability"))
    implementation(project(":infra:mysql"))        // springboot.jpa, querydsl 포함
    implementation(project(":infra:redis"))

    // infra 모듈이 api로 노출하는 것은 중복 선언 금지
    // 직접 필요한 것만 추가

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)        // MockMvc 포함
    testImplementation(libs.bundles.testcontainers)
}
```

## Key Patterns Reference

| 패턴 | 참조 파일 |
|------|----------|
| OutBox 이벤트 발행 | `module/outbox/src/.../OutBoxService.kt` |
| Cache-aside + Redis fallback | `infra/redis/src/.../ResilientCache.kt` |
| 분산락 | `infra/redis/src/.../DistributedLockAspect.kt` |
| Repository + Caching | `module/member/src/.../MemberRepositoryImpl.kt` |
| OAuth2 인증 플로우 | `module/auth/src/.../AuthService.kt` |
| Entity 컨벤션 | `module/member/src/.../Member.kt` |

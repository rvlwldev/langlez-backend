# Refactoring Plan

현재 코드베이스 분석을 통해 도출된 리팩토링 항목들.
**우선순위 순서**대로 작업할 것.

---

## 1. `module/auth` — OAuth2 로그인 연결 및 구조 수정 (최우선)

현재 로그인이 동작하지 않는 상태. 가장 먼저 처리해야 함.

### 1-1. `oauth2Login` Security Chain 연결

`WebSecurityConfiguration.filterChain`에 `.oauth2Login`이 없어서 `AuthService`, `OAuth2SuccessHandler`가 연결되지 않음.

`common/security`가 `module/auth`에 컴파일 타임 의존하지 않도록 **Spring Security 인터페이스**로 주입받아야 함.

```kotlin
// WebSecurityConfiguration.kt
class WebSecurityConfiguration(
    @param:Lazy private val oauth2UserService: OAuth2UserService<OAuth2UserRequest, OAuth2User>?,
    @param:Lazy private val oauth2SuccessHandler: AuthenticationSuccessHandler?,
) {
    fun filterChain(http: HttpSecurity, filter: JwtAuthenticationFilter): SecurityFilterChain =
        http
            // ... 기존 설정
            .oauth2Login { oauth2 ->
                oauth2UserService?.let { oauth2.userInfoEndpoint { it.userService(oauth2UserService) } }
                oauth2SuccessHandler?.let { oauth2.successHandler(it) }
            }
            .build()
}
```

### 1-2. `loadUser` 트랜잭션 범위 분리

현재 `@Transactional` 안에 Google/Apple 외부 HTTP 호출이 포함되어 있어 DB 커넥션을 불필요하게 오래 점유함.

```kotlin
// 현재 — 문제
@Transactional
override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
    val user = delegate.loadUser(userRequest)  // ← 외부 HTTP 호출
    // ... DB 작업
}

// 수정 — HTTP 호출과 DB 작업 분리
override fun loadUser(userRequest: OAuth2UserRequest): OAuth2User {
    val user = delegate.loadUser(userRequest)  // 트랜잭션 밖
    val profile = OAuth2UserProfile.by(...)
    return processLogin(profile)               // 트랜잭션 안
}

@Transactional
fun processLogin(profile: OAuth2UserProfile): OAuth2User { ... }
```

### 1-3. `AuthService` 의존성 정리

현재 `MemberRepository`와 `MemberService`를 동시에 의존하며 역할이 불명확함.
`MemberRepository` 직접 의존을 제거하고 `MemberService`로 일원화.

`MemberService`에 추가 필요한 메서드:
- `findByProvider(providerId, providerType)` — 로그인 시 기존 회원 조회
- `findByEmail(email)` — 이메일 중복 확인
- `updateLastAccess(id)` — 마지막 접속 시각 갱신

### 1-4. 로그아웃 엔드포인트 추가

`POST /api/v1/auth/logout` — Redis의 `refresh_token:{id}` 삭제.
현재 토큰 무효화 수단이 없음.

```kotlin
@PostMapping("/logout")
@ResponseStatus(NO_CONTENT)
fun logout(@MemberID memberId: Long) = service.logout(memberId)
```

### 1-5. `Google name` 안전 캐스팅

```kotlin
// 현재 — NPE 위험
displayName = attributes["name"] as String

// 수정
displayName = attributes["name"] as? String ?: "GoogleUser"
```

---

## 2. `common` 단일 모듈로 통합

현재 `common/` 하위 모듈들(`web`, `security`, `jackson`, `exception`, `observability`)이 전부 공통 설정/유틸리티 성격으로 굳이 나눌 이유가 없음.
`core`처럼 `common` 자체를 단일 모듈로 만들어 의존 선언을 단순화.

통합 후 구조:
```
common/
  src/main/kotlin/com/langlez/
    security/         # WebSecurityConfiguration, JwtAuthenticationFilter, JwtParser
                      # @MemberID, @MemberRole, ArgumentResolver, ContextUser
    web/              # GlobalRestControllerAdvice, WebConfiguration (Swagger)
    jackson/          # JacksonConfiguration (ObjectMapper)
    exception/        # LanglezException, ExceptionResponse
    observability/    # P6Spy, Prometheus, ApplicationStateLogger
```

의존 선언 변화:
```kotlin
// 현재
implementation(project(":common:web"))
implementation(project(":common:security"))
implementation(project(":common:observability"))
implementation(project(":common:exception"))
implementation(project(":common:jackson"))

// 변경 후
implementation(project(":common"))
```

### 작업 내용

- `common/` 하위 모듈들의 소스를 단일 `common` 모듈로 통합
- `settings.gradle.kts`에서 하위 모듈 선언 제거, `common` 단일 모듈로 등록
- 모든 모듈의 `build.gradle.kts` 의존성을 `project(":common")`으로 일괄 변경

### 영향받는 모듈

`module/member`, `module/profile`, `module/relationship`, `module/auth`, `app/api`

---

## 3. `module/outbox` 제거 — 모듈별 자체 구현으로 전환

단일 `module/outbox`를 공유하면 모든 도메인 모듈이 outbox에 의존하게 되어 모듈 간 결합이 생김.
각 도메인 모듈이 자체 Outbox 테이블과 스케줄러를 소유하는 방식으로 전환.
마이크로서비스 전환 시 모듈 분리가 용이해짐.

### 구조

`core`에 Outbox 추상화를 두고 각 모듈이 상속해서 자체 테이블로 관리.

```kotlin
// core/OutBoxEntity.kt — 공통 추상화
abstract class OutBoxEntity(
    val aggregateType: String,
    val aggregateId: String,
    val eventName: String,
    val payload: String,
    var status: Status = Status.PENDING,
) {
    enum class Status { PENDING, DISPATCHED, COMPLETED, FAILED }
}
```

```
module/member/
  outbox/
    MemberOutBox.kt          # OutBoxEntity 상속
    MemberOutBoxRepository.kt
    MemberOutBoxScheduler.kt  # Kafka 발행 + 아카이빙

module/relationship/
  outbox/
    RelationshipOutBox.kt
    RelationshipOutBoxRepository.kt
    RelationshipOutBoxScheduler.kt
```

Kafka 발행 공통 로직은 `infra/kafka`에 유틸리티로 제공하여 스케줄러 구현 중복 최소화.

### 작업 내용

- `module/outbox` 모듈 제거
- `core`에 `OutBoxEntity` 추상 클래스 추가
- 각 도메인 모듈에 자체 Outbox 엔티티, 리포지토리, 스케줄러 구현
- `core/OutBoxEventPublisher` 인터페이스 → 도메인 이벤트 패턴으로 대체되므로 제거

---

## 4. `module/member` 리팩토링

### 4-1. `MemberProvider` 제거 및 Member 플랫화

`MemberProvider`는 3개 필드뿐인 `@Embeddable`. 멀티 프로바이더 지원 계획이 없으므로 `Member`에 직접 포함.

```kotlin
// 제거
@Embedded val provider: MemberProvider

// 추가
@Enumerated(STRING) val provider: Provider
@Column(name = "provider_id") val providerId: String
val providerDisplayName: String  // Apple은 최초 로그인 시에만 이름을 제공하므로 반드시 저장
```

### 4-2. 도메인 이벤트 패턴 적용

현재 `MemberService`가 `OutBoxEventPublisher`를 직접 호출해 인프라 개념이 서비스에 노출됨.

**변경 방향**: 엔티티가 도메인 이벤트를 등록 → `@TransactionalEventListener(BEFORE_COMMIT)`로 Outbox에 저장.

```kotlin
// Member.kt
@Transient private val events = mutableListOf<Any>()

@DomainEvents private fun events(): List<Any> = events.toList()
@AfterDomainEventPublication private fun clearEvents() = events.clear()

@PostPersist
private fun onCreated() {
    events.add(MemberEvent.Created(id, email, username, nickname))
}

fun changeUsername(newUsername: String) {
    username = newUsername
    lastUsernameUpdatedAt = Instant.now()
    events.add(MemberEvent.UsernameChanged(id, newUsername))
}
```

```kotlin
// MemberEventHandler.kt
@Component
class MemberEventHandler(private val outboxRepo: MemberOutBoxRepository, private val mapper: ObjectMapper) {

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun handle(event: MemberEvent.Created) {
        outboxRepo.save(MemberOutBox("MEMBER", event.id.toString(), "member-created", mapper.writeValueAsString(event)))
    }

    @TransactionalEventListener(phase = BEFORE_COMMIT)
    fun handle(event: MemberEvent.UsernameChanged) {
        outboxRepo.save(MemberOutBox("MEMBER", event.id.toString(), "member-username-changed", mapper.writeValueAsString(event)))
    }
}
```

### 4-3. `MemberService`에서 `OutBoxEventPublisher` 의존 제거

도메인 이벤트 패턴 적용 후 `MemberService`는 `repo.save()`만 호출하면 됨.

### 4-4. `Member` 엔티티 수정 사항

```kotlin
// 수정 전
var lastLoggedInAt: Instant? = null

// 수정 후 — 이름 변경
var lastAccessedAt: Instant = Instant.now()

// 누락된 기본값 추가
var premiumExpiresAt: Instant? = null
var lastUsernameUpdatedAt: Instant? = null
var lastNicknameUpdatedAt: Instant? = null

// @EntityListeners 추가 (@CreatedDate 동작에 필요)
@EntityListeners(AuditingEntityListener::class)

// 테이블명 컨벤션 통일 (소문자 snake_case)
@Table(name = "members")

// provider 컬럼명 명시 (UniqueConstraint와 일치시키기 위해)
@Column(name = "provider_type")
@Enumerated(STRING) val provider: Provider
```

### 4-5. `MemberEvent` 정리

```kotlin
sealed interface MemberEvent {
    data class Created(val id: Long, val email: String, val username: String, val nickname: String) : MemberEvent
    data class UsernameChanged(val id: Long, val newUsername: String) : MemberEvent
    data class NicknameChanged(val id: Long, val newNickname: String) : MemberEvent
    // Login 이벤트 불필요 — 제거
}
```

---

## 5. `module/profile` 버그 수정 및 리팩토링

### 5-1. [Critical] `changeRepresentImage` UniqueConstraint 위반 버그

현재 기존 이미지를 대표로 변경할 때 동일 URL로 새 엔티티를 INSERT 시도해 `UNQ_PROFILE_IMAGE_URL` 제약 위반 발생.

```kotlin
// 수정 — changeRepresentImage는 replaceRepresentImage를 쓰지 않고 별도 처리
fun changeRepresentImage(memberId: Long, fileUrl: String): ProfileImage =
    transaction.execute {
        val target = repo.findImageByUrl(memberId, fileUrl)
            ?: throw LanglezException(NOT_FOUND, "profile.image.not-found")
        repo.findRepresentImage(memberId)?.apply {
            this.represent = false
            repo.saveImage(this)
        }
        target.represent = true
        repo.saveImage(target)
    } ?: throw LanglezException()
```

### 5-2. [Critical] `confirmAdditionalImage` TOCTOU 동시성 버그

이미지 수 체크가 트랜잭션 밖에서 실행되어 동시 요청 시 MAX_IMAGES를 초과할 수 있음.

```kotlin
// 수정 — 체크와 저장을 같은 트랜잭션으로
fun confirmAdditionalImage(memberId: Long, fileUrl: String): ProfileImage =
    transaction.execute {
        if (repo.countImages(memberId) >= MAX_IMAGES)
            throw LanglezException(BAD_REQUEST, "profile.image.limit-exceeded")
        val sequence = repo.countImages(memberId) + 1
        repo.saveImage(ProfileImage(memberId, fileUrl, sequence, 0L, false))
    } ?: throw LanglezException()
```

### 5-3. [Critical] `flushVisitCounts` — `KEYS` 명령 프로덕션 블로킹

`redis.keys("profile:visit:*")`는 Redis 싱글 스레드를 블로킹. `SCAN`으로 교체 필요.

```kotlin
// 수정
val keys = mutableListOf<String>()
var cursor = ScanCursor.INITIAL
do {
    val page = redis.scan(cursor, ScanOptions.scanOptions().match("$HLL_PREFIX*").count(100).build())
    keys.addAll(page.content)
    cursor = page
} while (!cursor.isLast)
```

### 5-4. [High] `flushVisitCounts` — PFCOUNT/DELETE 비원자적

PFCOUNT 후 DELETE 사이에 PFADD가 발생하면 방문 데이터 유실. Lua script로 원자화.

```kotlin
private val flushScript = RedisScript.of(
    "local c = redis.call('PFCOUNT', KEYS[1]); redis.call('DEL', KEYS[1]); return c",
    Long::class.java
)
```

또한 `VisitCountSyncScheduler`에서 Redis 데이터를 삭제한 후 DB 트랜잭션 실패 시 방문 카운트 완전 유실.
Redis 삭제를 DB 커밋 성공 이후로 순서 변경 필요.

### 5-5. [High] `ProfileController` — `MemberRepository` 직접 의존 제거

컨트롤러가 다른 모듈의 Repository를 직접 주입받는 레이어 위반.
`ProfileService`에 통합 메서드 제공.

```kotlin
// ProfileService에 추가
fun getProfileDetail(visitorId: Long, username: String): ProfileResponse.Detail

// ProfileController 수정
@GetMapping("/@{username}")
fun getProfile(@MemberID visitorId: Long, @PathVariable username: String): ProfileResponse.Detail =
    service.getProfileDetail(visitorId, username)
```

### 5-6. [Medium] `findProfileByUsername` 캐시 미적용

프로필 조회의 실제 진입 경로인데 캐시 없음. `findProfile(id)`에는 `@Cacheable` 있음.

```kotlin
@Cacheable(cacheNames = ["profile"], key = "'username:' + #username")
override fun findProfileByUsername(username: String): Profile? = ...

// saveProfile evict도 username 키 포함
@Caching(evict = [
    CacheEvict(cacheNames = ["profile"], key = "#profile.id"),
    CacheEvict(cacheNames = ["profile"], key = "'username:' + #profile.member.username"),
])
override fun saveProfile(profile: Profile): Profile = ...
```

### 5-7. [Medium] Virtual Thread 환경에서 불필요한 `CompletableFuture`

Java 21 Virtual Thread 환경에서 `CompletableFuture.supplyAsync`로 추가 스레드 생성은 이점이 없음.
`ExecutionException` 언래핑 복잡도만 추가됨. `ProfileService`에 통합 메서드로 단순화.

### 5-8. [Low] `Profile.version` nullable 통일

```kotlin
// 수정 전
@Version var version: Long? = null

// 수정 후 — Member와 동일하게
@Version var version: Long = 0
```

### 5-9. 프로필 수정 엔드포인트 추가

`PATCH /api/v1/profiles/me` — bio, goal, want, gender, mbti, locale, birthDay 수정.
현재 Profile 필드가 있지만 수정 API가 없음.

### 5-10. 프로필 이미지 삭제 엔드포인트 추가

`DELETE /api/v1/profiles/images` — `ProfileImage.deletedAt`이 있지만 사용하는 경로 없음.

---

## 6. `infra/redis` — Lettuce + Redisson 중복 제거, Redisson으로 통일

현재 Lettuce(Spring Data Redis)와 Redisson이 동시에 사용되어 커넥션 풀이 이중으로 유지됨.

**현재 역할 분리:**
- Lettuce (`StringRedisTemplate`, `RedisTemplate`) — auth 토큰 저장, profile HyperLogLog, 캐시
- Redisson (`RedissonClient`) — `@DistributedLock` AOP 전용

**Redisson으로 통일하는 이유:**
- Redisson은 `RBucket`, `RHyperLogLog`, `RMapCache` 등 고수준 자료구조를 제공해 Lettuce의 역할을 전부 대체 가능
- `@DistributedLock`이 이미 핵심 인프라로 자리잡고 있어 Redisson 제거가 더 부담
- Lettuce로 통일하면 분산 락을 Redlock 알고리즘으로 직접 구현해야 함

**변경 내용:**

```kotlin
// 현재 — Lettuce
redis.opsForValue().set("refresh_token:$id", refreshToken, 14, TimeUnit.DAYS)
redis.opsForValue().get("refresh_token:$id")

// 변경 후 — Redisson RBucket
val bucket = redisson.getBucket<String>("refresh_token:$id")
bucket.set(refreshToken, 14, TimeUnit.DAYS)
bucket.get()
```

```kotlin
// 현재 — Lettuce HyperLogLog
redis.opsForHyperLogLog().add(hllKey, visitorId)
redis.opsForHyperLogLog().size(hllKey)

// 변경 후 — Redisson RHyperLogLog
val hll = redisson.getHyperLogLog<Long>(hllKey)
hll.add(visitorId)
hll.count()
```

### 작업 내용

- `LettuceConfiguration` 제거
- `StringRedisTemplate`, `RedisTemplate` 의존을 전부 `RedissonClient`로 교체
- `ResilientCacheManager`를 Redisson 기반으로 전환 (Redisson Spring Cache 지원)
- `infra/redis/build.gradle.kts`에서 Lettuce 의존성 제거

---

## 작업 순서 요약

| 순서 | 항목 | 이유 |
|------|------|------|
| 1 | `module/auth` oauth2Login 연결 (1-1) | 로그인 자체가 안 되는 상태 |
| 2 | `module/profile` 버그 수정 (5-1, 5-2, 5-3) | 현재도 오류 발생하는 버그들 |
| 3 | `common` 단일 모듈 통합 | 이후 작업의 기반 |
| 4 | `infra/redis` Redisson 단일화 (6) | common 통합 후 진행, 커넥션 풀 중복 제거 |
| 5 | `module/outbox` 제거 및 모듈별 자체 구현 전환 | 구조 정리, member 리팩토링 선행 필요 |
| 6 | `module/member` 리팩토링 (4-1 ~ 4-5) | 도메인 이벤트 패턴 적용 |
| 7 | `module/profile` 나머지 수정 (5-5 ~ 5-10) | 레이어 위반, 캐시, 미구현 API |
| 8 | `module/auth` 구조 수정 (1-2, 1-3) | 기능은 되는 상태에서 개선 |

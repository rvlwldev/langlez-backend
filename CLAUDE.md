# Langlez Backend

Kotlin / Spring Boot 3.5.8 멀티모듈 백엔드. 언어교환 모바일 앱(iOS/Android)의 서버.

`module/member` 가 이 프로젝트의 **기준 모듈(reference module)** 이다. 새 모듈을 만들거나 기존 모듈을 고칠 때 member 모듈의 구조와 관례를 그대로 따른다. 이 문서는 member 모듈의 실제 코드에서 역으로 추출했고, **문서와 코드가 어긋나면 member 모듈 코드가 정답**이다.

현황과 남은 작업은 `PLAN.md`, 확정된 결함 목록은 `REVIEW.md` 를 본다.

---

## 0. 시작 전에 알아야 할 것

- **모바일 전용이다.** 쿠키를 쓰지 않는다. 토큰은 헤더로만 오간다.
- **1인 1기기 정책.** `X-Device-Id` 헤더가 필수고, 다른 기기로 로그인하면 기존 세션이 끊긴다.
- **`ddl-auto: validate`.** 스키마 변경은 Flyway 로만 한다. 엔티티와 마이그레이션이 어긋나면 기동 시점에 죽는다.
- **`open-in-view: false`.** 트랜잭션 밖에서 LAZY 연관을 만지면 터진다.
- **탈퇴 회원 데이터는 지우지 않는다.** 익명화도 안 한다. 재가입 후 같은 문제를 반복하는 회원 추적이 목적인 의도된 정책이다.

### 검증

```bash
./gradlew build   # 전체 빌드 + 테스트. 통합테스트가 Testcontainers(Postgres·Redis·Mongo)를 띄우므로 Docker 필요
```

---

## 1. 모듈 구조

```
core            프레임워크 없는 순수 계약 (포트 인터페이스 + 이벤트 DTO). 의존성 0
common          웹·보안·예외·필터·i18n 공용
infra/rdb       JPA + QueryDSL + Outbox 베이스 + Flyway
infra/redis     Redisson, 캐시 어댑터, 분산 락, pub/sub 브로드캐스터
infra/kafka     프로듀서·컨슈머 설정, DLT
module/*        도메인 모듈 (api / application / domain / infrastructure 4계층)
app/api         조립 + 실행
```

`settings.gradle.kts` 가 `module/` 하위를 자동 스캔한다. `build.gradle.kts` 만 만들면 등록된다.

### 4계층

모든 도메인 모듈은 아래 4계층을 갖는다. 계층 이름과 depth 를 임의로 바꾸지 않는다.

```
module/member/src/main/kotlin/com/langlez/member/
├── api/                        # 외부 진입점 (HTTP, Kafka, 애플리케이션 이벤트)
│   ├── MemberAPI.kt            # Swagger 문서 전용 인터페이스
│   ├── MemberController.kt     # MemberAPI 구현, Spring MVC 매핑만
│   ├── MemberPingController.kt # 접속 핑 (레디스 직결)
│   ├── MemberEventListener.kt  # @TransactionalEventListener → Outbox 기록
│   ├── request/                # 요청 DTO
│   └── response/               # 응답 DTO
├── application/                # 유스케이스 조합, 트랜잭션 경계
├── domain/                     # 엔티티 + 저장소 포트(인터페이스)
│   ├── Member.kt
│   └── MemberRepository.kt     # 인터페이스 (구현체 없음)
└── infrastructure/             # 포트의 구현(어댑터)
    ├── MemberRepositoryImpl.kt
    ├── jpa/                    # Spring Data 인터페이스만
    └── outbox/                 # Outbox 엔티티 + 스케줄러
```

**의존 방향은 한쪽으로만 흐른다.**

```
api ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` 은 다른 계층을 import 하지 않는다. 프레임워크 의존은 영속성/감사 애노테이션(`@Entity`, `@CreatedDate`, `AuditingEntityListener`)까지만. 웹/HTTP 타입(`HttpStatus`, `LanglezException`)은 넣지 않는다 — 불변식은 `require` 로 던지고 변환은 application 이 한다.
- `application` 은 `domain` 의 포트 인터페이스만 안다. `infrastructure` 구현 클래스를 직접 참조하지 않는다.
- `infrastructure` 가 `domain` 인터페이스를 구현하며 방향을 뒤집는다.
- 모듈 간에는 서로를 직접 참조하지 않는다. `core` 의 포트와 이벤트를 거친다.

### 모듈 간 통신 수단 선택

| 목적 | 수단 |
|---|---|
| 모듈 간 상태 변경 전파, 유실되면 안 되는 것 | **Kafka** (아웃박스 경유) |
| 응답을 기다려야 하는 조회 | **`core` 포트** |
| 접속 중인 사용자에게 실시간 전달 | **`core.MessageBroadcaster`** → Redis pub/sub → WebSocket |
| 고빈도 하트비트 | **Redis 직결** (Kafka 금지) |

고빈도·저가치 신호를 브로커에 태우면 비용만 든다. 접속 핑(5초 간격)이 그랬다 — 브로커 왕복에 handle→id 조회까지 붙어 있었다. 지금은 `MemberPingController` 가 레디스 버킷에 바로 쓴다.

현재 `core` 포트: `BlockQuery`, `FollowQuery`, `PushTokenQuery`, `Storage`, `OnlineTracker`, `CacheProvider`, `Notificator`, `MessageBroadcaster`, `TokenBlacklist`.

### 저장소 분담

| 데이터 | 저장소 | 이유 |
|---|---|---|
| 회원·프로필·방·참여자·아웃박스 | PostgreSQL | 조인·트랜잭션 필요, 유한 증가 |
| 채팅 메시지 본문 + 첨부 | MongoDB | 무한 증가, 첨부 임베드로 조회 1회 |
| 접속·화면 상태·분산 락·캐시·wave 채팅 | Redis | 휘발성·고빈도 |

---

## 2. 네이밍

| 역할 | 규칙 | 예시 |
|---|---|---|
| 엔티티 | 도메인 명사, 접미사 없음 | `Member`, `MemberAudit` |
| 저장소 포트 | `{Entity}Repository` (domain) | `MemberRepository` |
| 저장소 어댑터 | `{Entity}RepositoryImpl` (infrastructure) | `MemberRepositoryImpl` |
| Spring Data 인터페이스 | `{Entity}JpaRepository` (infrastructure/jpa) | `MemberJpaRepository` |
| Swagger 문서 인터페이스 | `{Domain}API` | `MemberAPI` |
| 요청 DTO | `{Domain}{동사}{대상}Request` | `MemberUpdateHandleRequest` |
| 응답 DTO | `{Domain}{범위}Response` | `MemberMeResponse`, `MemberPublicResponse` |
| Outbox 스케줄러 | `{Domain}OutBoxScheduler` | `MemberOutBoxScheduler` |

주입받는 의존성은 **짧은 관용 이름**을 쓴다. 타입명을 그대로 반복하지 않는다.

```kotlin
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,   // Spring Data
    private val dsl: JPAQueryFactory,       // QueryDSL
    caches: CacheProvider,                  // 캐시
) : MemberRepository
```

관용 축약: `repo`(저장소 포트), `jpa`, `dsl`, `caches`/`cache`, `tx`(TransactionTemplate), `mq`(MessageProducer), `publisher`(ApplicationEventPublisher), `mapper`(ObjectMapper), `service`.

`memberRepository`, `memberJpaRepository` 처럼 클래스명을 통째로 복붙한 파라미터명은 쓰지 않는다.

---

## 3. 도메인 엔티티

`Member.kt` 가 표준형이다.

```kotlin
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(
    name = "members",
    uniqueConstraints = [UniqueConstraint("UNQ_MEMBER_HANDLE", ["handle"])]
)
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val email: String,

    @Column(length = 20) var handle: String = randomHandle(),
    @Enumerated(STRING) var status: Status = Status.CREATED,
    @Enumerated(STRING) @Column(name = "provider_type") var provider: Provider,

    @Version var version: Long = 0
) {
    fun changeHandle(newHandle: String, now: Instant = Instant.now()) {
        require(canChangeHandle(now)) { "member.handle.cooldown" }
        require(isValidHandle(newHandle)) { "member.handle.invalid" }

        handle = newHandle
        audit.lastHandleUpdatedAt = now
    }

    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }

    companion object {
        const val HANDLE_REGEX = "^[a-zA-Z0-9_.]{3,20}$"
        fun isValidHandle(handle: String): Boolean = HANDLE_PATTERN.matches(handle)
    }
}
```

1. **`data class` 를 쓰지 않는다.** 일반 `class` + 주생성자 + 기본값. JPA 엔티티에 `equals`/`hashCode`/`copy` 자동 생성은 해롭다. DTO 만 `data class` 다.
2. **PK 는 기본적으로 `@Id @GeneratedValue(IDENTITY) val id: Long = 0`.** 항상 `val`, 기본값 `0`. 식별관계는 예외 — 부모 PK 를 공유하면 `@MapsId`(`Profile`), 복합키면 `@IdClass`(`ProfileImage`).
3. **enum 은 반드시 `@Enumerated(STRING)`.** ordinal 저장 금지.
4. **enum 과 상수는 엔티티 안에 중첩한다.** `Member.Status`, `Member.HANDLE_REGEX`. 최상위로 빼지 않는다.
5. **비즈니스 규칙은 엔티티 메서드로.** 서비스에서 `member.status = SUSPENDED` 처럼 필드를 직접 조작하지 않고 `member.suspend()` 를 호출한다. 불변식은 메서드 안에서 `require` 로 지킨다.
6. **`require` 의 메시지는 i18n 메시지 키다.** `require(...) { "member.handle.cooldown" }` — 사람이 읽는 문장을 넣지 않는다. 키 형식은 `{도메인}.{대상}.{사유}` (점 구분, 각 마디는 kebab-case).
7. **제약조건에 이름을 붙인다.** 유니크는 `UNQ_{TABLE}_{COLUMN}`, 인덱스는 `IDX_{TABLE}_{COLUMN}`. 이름 없는 제약은 DB 에서 추적이 안 된다.
8. **컬럼명이 프로퍼티명과 다르면 `@Column(name = ...)` 을 명시한다.** (`provider` → `provider_type`)
9. **시각은 `Instant`.** `LocalDateTime`/`Date` 안 쓴다. 시각이 아니라 **날짜**면 `LocalDate`(`Member.birthDay`). 시각 인자는 `now: Instant = Instant.now()` 로 받아 테스트에서 주입 가능하게 한다.
10. **낙관적 락이 필요한 엔티티는 `@Version`.**
11. **감사 필드가 여러 개면 별도 엔티티로 분리한다.** (`MemberAudit`, `@OneToOne(fetch = LAZY, cascade = [ALL], orphanRemoval = true)`)

### 검증 규칙은 한 곳에서만 정의한다

`HANDLE_REGEX` 는 엔티티 companion 에 있고 요청 DTO 가 그걸 참조한다. 정규식을 두 군데 적지 않는다.

```kotlin
// domain/Member.kt
const val HANDLE_REGEX = "^[a-zA-Z0-9_.]{3,20}$"

// api/request/MemberUpdateHandleRequest.kt
@field:Pattern(regexp = Member.HANDLE_REGEX, message = "member.handle.invalid")
val handle: String,
```

---

## 4. 저장소: 포트와 어댑터

### 포트 (domain)

순수 인터페이스. 프레임워크 타입(`Page`, `Pageable`, `Optional`)을 노출하지 않는다. 페이징은 커서 기반으로 `size`, `cursor` 를 직접 받는다.

```kotlin
interface MemberRepository {
    fun save(member: Member): Member

    fun find(id: Long): Member?
    fun find(handle: String): Member?
    fun find(provider: Member.Provider, id: String): Member?
    fun findByEmail(email: String): Member?

    fun findAll(ids: Collection<Long>): List<Member>
    fun findAll(size: Int, cursor: Long?): List<Member>

    fun delete(id: Long)
    fun delete(member: Member)
}
```

- **단건 조회는 `find` 로 오버로딩한다.** `findById`, `findByHandle` 처럼 이름을 늘리지 않는다. 파라미터 타입이 이미 의도를 말한다. 타입이 겹쳐 구분이 안 될 때만 `findByEmail` 처럼 이름을 붙인다.
- **없으면 `null` 을 반환한다.** 예외를 던지지 않는다. 예외 변환은 application 계층 몫이다.
- 복수 조회는 `findAll`, 반환은 `List<T>`.

### 어댑터 (infrastructure)

1. **캐시는 `core.CacheProvider` 포트를 쓴다.** Spring 의 `@Cacheable`/`CacheManager` 는 쓰지 않는다. 어노테이션 기반 캐시는 self-invocation 에 취약하고 갱신 시점이 코드에 안 보인다. (Spring `Cache` 에 multi-get/multi-set 이 없어 컬렉션 조회가 건당 왕복으로 쪼개지는 것도 이유였다.)
2. **2단계 캐시 구조.** 보조 캐시(`member-handle`, `member-email`, `member-provider`)는 **PK 만 문자열로** 저장하고, 실제 엔티티는 `member` 캐시 한 곳에만 둔다. 엔티티를 여러 캐시에 복제하면 갱신 시 반드시 어긋난다.
3. **`updateCaches` / `evictCaches` 는 항상 대칭 쌍.** 한쪽에 캐시를 추가했으면 반대쪽에도 넣는다.
4. **바뀔 수 있는 값을 캐시 키로 쓰면 읽을 때 반드시 재검증한다.** 구 키가 TTL 까지 남는다. 구 키를 지우는 것만으로는 부족하다 — 캐시는 노드마다 따로 있고 로컬 폴백 캐시는 같은 객체 참조를 돌려주기도 해서 "이전 값"을 신뢰할 수 없다.
   ```kotlin
   val id = handles.get<String>(handle)?.toLongOrNull()
   // handle 은 바뀔 수 있는 키다. 캐시로 찾은 회원의 handle 이 다르면 낡은 항목이다.
   val cached = id?.let(jpa::findByIdOrNull)?.takeIf { it.handle == handle }
   if (cached == null && id != null) handles.evict(handle)
   ```
5. **LAZY 연관을 가진 엔티티는 캐시하지 않는다.** 캐시에서 꺼낸 엔티티는 detached 라 연관 변경이 `merge` 로 전파되지 않고, 초기화 안 된 프록시가 직렬화되며, 오래된 `@Version` 을 되써서 `OptimisticLockException` 을 부른다. 캐시가 필요하면 필요한 필드만 담은 값 객체를 넣는다.
6. **동적·복합 조건 조회는 QueryDSL.** 조건을 메서드명으로 잇는 파생 쿼리(`findByHandleAndStatusAndDeletedAtIsNullOrderBy...`)는 만들지 않는다. 다만 아래는 파생 쿼리가 낫다 — QueryDSL 로 쓰면 오히려 장황해진다:
   - `@EntityGraph` 를 붙여야 하는 조회 (`findWithAuditById`)
   - 단일 조건 존재 확인 (`existsByBlockerIdAndBlockedId`)
   - soft delete 필터가 붙은 단순 조회 (`findByIdAndDeletedAtIsNull`)
7. **QueryDSL Q타입은 별칭 import 한다.** `import com.langlez.member.domain.QMember.Companion.member as QMember`
8. **컬렉션 인자는 빈 값을 먼저 걷어낸다.** `if (ids.isEmpty()) return emptyList()` — 빈 `IN ()` 쿼리를 막는다. 중복은 `toSet()` 으로 제거.
9. **일괄 삭제는 조건부로 `deleteAllInBatch`.** `deleteAll` 은 건수만큼 단건 DELETE 라 느리다. 다만 `deleteAllInBatch` 는 **영속성 컨텍스트를 우회하므로 `cascade`/`orphanRemoval` 이 걸린 연관이 있으면 쓰면 안 된다** — 자식 행이 고아로 남는다. (`Member` 는 `audit` 에 `cascade = [ALL], orphanRemoval = true` 가 있어 `deleteAll` 을 쓴다.)

---

## 5. 애플리케이션 계층

```kotlin
@Service
class MemberService(
    private val repo: MemberRepository,
    private val tracker: OnlineTracker,
    private val publisher: ApplicationEventPublisher,
) {

    @Transactional(readOnly = true)
    fun findById(id: Long): Member? = repo.find(id)

    @Transactional
    fun updateHandle(id: Long, newHandle: String): Member {
        if (repo.find(newHandle) != null)
            throw LanglezException(HttpStatus.CONFLICT, "member.handle.duplicated")

        val member = findOrThrow(id)

        try {
            member.changeHandle(newHandle)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(HttpStatus.BAD_REQUEST, e.message, e)
        }

        return runCatching { repo.save(member) }
            .getOrElse { e -> throw LanglezException(HttpStatus.CONFLICT, "member.handle.duplicated", e) }
            .also { publisher.publishEvent(MemberHandleChangedEvent(id, member.handle)) }
    }

    private fun findOrThrow(id: Long) = repo.find(id)
        ?: throw LanglezException(HttpStatus.NOT_FOUND, "member.not-found")
}
```

### 트랜잭션

- **읽기 전용은 `@Transactional(readOnly = true)`, 쓰기는 `@Transactional`.** 클래스 레벨에 걸지 않고 메서드마다 명시한다.
- **일부러 트랜잭션을 걸지 않았다면 이유를 주석으로 남긴다.** 다음 사람이 "누락"으로 보고 되돌리는 걸 막는 게 목적이다.
  ```kotlin
  // storage.attach() 가 S3 HeadObject 등 블로킹 I/O 라 트랜잭션 밖에서 먼저 끝낸다.
  // repo.save() 가 자기 트랜잭션을 가지니 여기서 따로 @Transactional 을 걸 필요 없다.
  fun updateProfileUrl(id: Long, key: String): Member { ... }
  ```
- **네트워크 I/O(S3, 외부 API)는 DB 트랜잭션 안에 넣지 않는다.** 커넥션을 잡은 채 외부를 기다리면 풀이 마른다.

### 예외

- **application 계층은 `LanglezException(HttpStatus, 메시지키)` 만 던진다.** 메시지 자리에는 i18n 키.
- **도메인의 `IllegalArgumentException` 을 그대로 흘리지 않는다.** `try/catch` 로 잡아 상태코드를 붙여 변환하고, 원인 예외를 세 번째 인자로 넘겨 스택을 보존한다.
- **`findOrThrow` 는 private 헬퍼.** 키 타입별로 오버로딩해 중복 `?: throw` 를 없앤다.
- **유니크 제약 경합은 `@Retryable(retryFor = [DataIntegrityViolationException::class])`** 로 흡수한다 (랜덤 handle 충돌 등).

### Kotlin 관용구

- 상태 변경 후 저장은 `apply { }` + `also(repo::save)` 체인. — `findOrThrow(id).apply { fcm = token }.also(repo::save)`
- 단일 표현식 함수는 `=` 본문.
- **실패해도 주 흐름을 막으면 안 되는 부수 효과는 `runCatching`.** `.apply { runCatching { tracker.toOnline(id) } }` — 온라인 표시 실패로 가입을 실패시키지 않는다. 단, 삼켜도 되는 실패에만. 데이터 정합성이 걸린 곳엔 쓰지 않는다.

### 이벤트

- 도메인 이벤트 DTO 는 `core/event/{domain}/` 에 `data class` 로 둔다. 모듈 간 공유 계약이다.
- 발행은 `ApplicationEventPublisher.publishEvent`.
- 수신 후 Outbox 기록은 **`@TransactionalEventListener(phase = BEFORE_COMMIT)`**. 원 트랜잭션이 아직 열려 있어 Outbox insert 가 같은 트랜잭션에 묶이고 롤백 시 함께 사라진다. `AFTER_COMMIT` 을 쓰면 이벤트만 남고 원본이 롤백되는 불일치가 생긴다.

---

## 6. API 계층

### Swagger 문서와 컨트롤러 분리

**이 프로젝트의 핵심 관례다.** 컨트롤러에 `@Operation`, `@Schema` 를 직접 붙이지 않는다. 문서는 `{Domain}API` 인터페이스에 몰고 컨트롤러는 그걸 구현하며 Spring MVC 매핑만 갖는다.

```kotlin
// api/MemberAPI.kt — 문서 전용
@Tag(name = "Member", description = "회원 계정 관리 API")
interface MemberAPI {
    @Operation(summary = "핸들 변경", description = "15일 쿨다운 및 중복 검사가 있다.")
    fun patchHandle(memberId: Long, request: MemberUpdateHandleRequest): MemberMeResponse
}

// api/MemberController.kt — 매핑 전용
@RestController
@RequestMapping("/api/v1/members")
class MemberController(private val service: MemberService, private val repo: MemberRepository) : MemberAPI {

    @PatchMapping("/me/handle")
    override fun patchHandle(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdateHandleRequest
    ): MemberMeResponse = MemberMeResponse(service.updateHandle(memberId, request.handle))
}
```

- 경로는 `/api/v1/{복수형 도메인}`. 본인 리소스는 `/me` 하위.
- **인증된 사용자 ID 는 `@MemberId memberId: Long` 으로만 받는다.** 본문에서 받으면 남을 사칭할 수 있다. `Principal`/`SecurityContextHolder` 를 컨트롤러에서 직접 뒤지지 않는다.
- 요청 바디는 `@RequestBody @Valid` 를 항상 함께.
- 본문 없는 응답은 `@ResponseStatus(HttpStatus.NO_CONTENT)`.
- **컨트롤러는 로직을 갖지 않는다.** 단순 조회는 서비스를 거치지 않고 `repo` 를 직접 주입받아 써도 된다.
- **엔티티를 그대로 반환하지 않는다.** 항상 응답 DTO 로 변환한다.
- **모든 방 단위·소유자 단위 접근은 권한 검사를 거친다(IDOR 방지).** 인증만 통과했다고 남의 리소스에 닿으면 안 된다. WebSocket SUBSCRIBE 도 마찬가지다.

### DTO

```kotlin
data class MemberMeResponse(
    @field:Schema(description = "이메일") val email: String,
    @field:Schema(description = "프로필 이미지 URL", nullable = true) val imageUrl: String?,
) {
    constructor(member: Member) : this(email = member.email, imageUrl = member.imageUrl)
}
```

- DTO 는 `data class`, 모든 프로퍼티 `val`.
- **어노테이션에 `@field:` 타깃을 명시한다.** (`@field:Schema`, `@field:NotBlank`, `@field:Pattern`) 타깃을 생략하면 파라미터에 붙어 런타임에 무시될 수 있다.
- **엔티티 → 응답 변환은 보조 생성자로.** 별도 Mapper 클래스나 `toResponse()` 확장함수를 만들지 않는다.
- 응답은 노출 범위별로 나눈다. 본인용 `MemberMeResponse`(이메일 포함), 타인용 `MemberPublicResponse`(handle/role 만). 하나의 DTO 에 nullable 필드를 섞어 재사용하지 않는다.

### 파일 업로드

`core.Storage.presign` 으로 presigned URL 을 내주고, 확정은 **key 로만** 받는다. **클라이언트가 준 URL 을 그대로 저장하면 외부 주소를 심을 수 있다.** (`module/chat`, `module/profile`, `module/echo` 가 같은 패턴)

---

## 7. 비동기 / 스케줄링

### Outbox

DB 트랜잭션과 메시지 발행의 원자성이 필요하면 Outbox 를 쓴다. `infra:rdb` 의 `OutBox`, `OutBoxRepository`, `OutBoxProcessor` 를 상속한다.

```kotlin
@Entity
@Table(name = "member_event_outbox")
class MemberOutBox(domain: String, topic: String, payload: String, key: String? = null)
    : OutBox(domain, topic, payload, key)

@Repository
interface MemberOutBoxRepository : OutBoxRepository<MemberOutBox>

@Component
internal class MemberOutBoxScheduler(repo: MemberOutBoxRepository) : OutBoxProcessor<MemberOutBox>(repo) {
    override val chunk = 1000

    @Scheduled(cron = "*/2 * * * * *")
    @DistributedLock(prefix = "lock:member-outbox")
    override fun send() = super.send()
}
```

예외: **가장 빈번한 쓰기(채팅 메시지)는 별도 아웃박스 행 대신 문서의 `published` 플래그**로 처리해 쓰기 증폭을 없앴다.

### 스케줄러

- **`@Scheduled` 가 붙은 메서드에는 `@DistributedLock` 을 반드시 함께 건다.** 서버가 여러 대일 때 중복 실행을 막는 유일한 장치다. prefix 는 `lock:{용도}`.
- **`@DistributedLock` 은 스케줄러 전용이 아니다.** 여러 인스턴스에서 동시 실행되면 안 되는 쓰기(개수 제한 검사 후 삽입 등)에도 `@DistributedLock(transactional = true)` 를 건다.
- 스케줄러 클래스는 모듈 밖에서 부를 일이 없으면 `internal`.
- 튜닝 상수(`chunk`, `tries`, `threads`)는 부모의 `open val` 을 override 해 조정한다.

### Kafka 컨슈머

`api/{Domain}Consumer.kt` 에 둔다. 외부 메시지 계약과 내부 모델이 다르면 **컨슈머에서 변환하고 왜 변환하는지 주석을 남긴다.**

```kotlin
@KafkaListener(topics = ["chat-message-sent"], groupId = "notification")
fun onChatMessageSent(event: ChatMessageSentEvent) { ... }
```

---

## 8. 스키마 마이그레이션 (Flyway)

- 파일 위치: `infra/rdb/src/main/resources/migration/V{n}__*.sql`
- 운영·개발·테스트 **모두 `ddl-auto: validate`**. 통합테스트도 Flyway 를 타므로 마이그레이션 자체가 검증된다.
- **이미 적용된 V 파일은 절대 수정하지 않는다.** 체크섬 불일치로 기동이 실패한다. 고칠 게 있으면 새 V 파일을 만든다.
- **`@Column(nullable = false)` 를 새로 붙이면 기존 행 백필 마이그레이션이 반드시 따라와야 한다.** 안 하면 NULL 을 읽어 Kotlin non-null 프로퍼티에서 NPE 가 난다.
- 정렬·커서는 `created_at` 이 아니라 **id 시퀀스**나 도메인 시퀀스 기준. 인스턴스 간 시계 차이로 순서가 뒤집힌다.

---

## 9. i18n

**신규 메시지 키는 `common/src/main/resources/messages_*.properties` 12개 전부에 등록한다.**
(ko, ja, en, de, es, fr, pt, id, ru, vi, zh_CN, zh_TW)

`GlobalRestControllerAdvice` 는 키를 못 찾으면 **키 문자열을 그대로 응답 본문에 담아 클라이언트에 내보낸다.** 누락이 조용히 넘어간다.

확인:
```bash
for f in common/src/main/resources/messages_*.properties; do echo "$(basename $f) $(grep -c '^[a-z].*=' $f)"; done
```
전부 같은 수여야 한다.

---

## 10. 주석

**한국어로 쓰고 "무엇"이 아니라 "왜"를 남긴다.** 코드를 보면 아는 내용은 적지 않는다.

반드시 남겨야 하는 곳:

1. **누가 보면 버그로 오해할 코드** — 되돌리려는 시도를 막는다.
   ```kotlin
   // 백킹 필드가 없는 파생 프로퍼티라 @field: 는 컴파일이 안 된다. getter 를 막아야 한다.
   @get:Transient
   ```
2. **의도적으로 생략한 것** — 트랜잭션, 락, 검증 등.
3. **성능/정합성 때문에 택한 구조** — KDoc 으로.
4. **외부 계약과 내부 모델이 어긋나는 지점.**

주의: 주석 안에 `/*` 가 들어가면(예: 토픽 패턴 `room/*`) Kotlin 중첩 주석이 열려 뒤 코드가 통째로 주석 처리된다. 표현을 바꿔 쓴다.

---

## 11. 테스트

### 단위 — Kotest `BehaviorSpec` + MockK

```kotlin
class MemberServiceTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = MemberService(repo, creator, tracker, storage, publisher, suspendRepo)

    afterEach { clearMocks(repo, publisher, answers = false) }

    fun member(id: Long = 1L, status: Member.Status = Member.Status.ACTIVE) = Member(
        id = id, email = "user$id@test.com", handle = "user$id",
        status = status, provider = Member.Provider.GOOGLE, providerId = "p$id",
    )

    Given("회원 정지 시") {
        When("이미 탈퇴한 회원을 정지하려 하면") {
            every { repo.find(2L) } returns member(id = 2L, status = Member.Status.WITHDRAWN)

            Then("400 LanglezException 이 발생한다") {
                val ex = shouldThrow<LanglezException> { service.suspendMember(2L) }
                ex.status.value() shouldBe 400
            }
        }
    }
})
```

- **Given/When/Then 설명은 한국어 서술형.** Given 은 상황, When 은 행위, Then 은 검증할 결과.
- 픽스처는 **스펙 안의 로컬 함수**로. 별도 `TestFixture` 클래스를 만들지 않는다. 기본값을 두고 필요한 필드만 덮어쓴다.
- `afterEach { clearMocks(..., answers = false) }` 로 호출 기록만 초기화. **`clearMocks` 를 스텁까지 지우게 두면 `Then` 블록 사이에 돌아 `verify` 가 빈 기록을 본다.**
- 검증하지 않을 협력자는 `mockk(relaxed = true)`.
- 단언은 kotest 매처(`shouldBe`, `shouldThrow`, `shouldHaveSize`). JUnit `assertEquals` 를 섞지 않는다.
- **"하지 않음"도 검증한다.** 의도적 설계는 테스트로 고정해야 나중에 안 깨진다.
  ```kotlin
  Then("핸들만 바뀌고 온라인 트래커는 건드리지 않는다 (id 로 keying 하므로)") {
      verify(exactly = 0) { tracker.toOnline(any()) }
  }
  ```

### 통합 — Testcontainers

- DB 는 **Testcontainers PostgreSQL**. H2 로 대체하지 않는다.
- 외부 인프라는 `@TestConfiguration` + `@Primary` + `mockk(relaxed = true)` 로 대체.
- `SpringBootTest` 를 쓸 땐 `override fun extensions() = listOf(SpringExtension)`, 본문은 `init { }` 블록.
- **부수 효과까지 검증한다.** 유스케이스 하나에 대해 "DB 반영 + Outbox 기록 + 실패 시 롤백"을 각각 `Then` 으로 나눠 확인한다.

### RED 를 진짜로 확인한다

테스트가 "통과해서" 넘어가지 말고 **먼저 실패하는 걸 눈으로 본다.** 이 저장소에서 relaxed mock 이 non-null 을 돌려주는 바람에 `OncePerRequestFilter` 가 아예 안 타고도 초록이 뜬 적이 있다. 의심되면 구현을 잠깐 되돌려 빨간불을 확인한다.

---

## 12. 코틀린 스타일

- 들여쓰기 4칸, 최대 줄 길이 120자.
- `import` 와일드카드 금지. 단 `jakarta.persistence.*` 처럼 엔티티에서 다수를 쓰는 경우는 허용.
- enum 상수는 개별 import 해 짧게 쓴다. `import jakarta.persistence.EnumType.STRING` → `@Enumerated(STRING)`
- nullable 처리는 `?.let`, `?:` 우선. `!!` 는 테스트 외에 쓰지 않는다.
- 함수 인자가 3개를 넘으면 호출 시 **이름 붙인 인자**.
- 클래스 밖으로 나갈 필요 없는 건 `private`, 모듈 밖으로 나갈 필요 없는 건 `internal`.
- 버전은 반드시 `libs.*` 버전 카탈로그 별칭으로 참조한다. 하드코딩 금지.

---

## 13. 반복해서 터진 함정

실제로 이 저장소에서 발생했던 것들. 전부 "조용히 잘못되는" 종류라 테스트 없이는 못 찾는다.

| 함정 | 증상 |
|---|---|
| 인증만 하고 인가 안 함 | 로그인한 아무나 남의 대화 구독. 와일드카드 토픽으로 전체 흡입 |
| fail-open 검증 (`x != null && x != y`) | 헤더를 빼기만 하면 검증 통째로 우회 |
| 클라이언트가 준 URL 저장 | 외부 주소 삽입, presigned 서명 노출 |
| 엔티티 읽고-쓰기로 카운터 증가 | 동시 요청 시 증가 유실. DB 단일 UPDATE 로 바꿔야 한다 |
| `open val` 을 베이스 생성자에서 읽기 | 하위 클래스 값이 아직 0. `Semaphore(0)` 으로 전체 정지. `by lazy` 로 미룬다 |
| Redisson 코덱이 final 타입에 `@class` 미부여 | 캐시 read 100% 실패 → 무한 플래핑 |
| `Long` 을 Redis 셋에 저장 | JSON 코덱으로 `Integer` 가 돌아와 `contains(1L)` 이 조용히 false |
| `@Modifying` 쿼리에 트랜잭션 없음 | `No EntityManager with actual transaction` |
| i18n 키 누락 | 키 문자열이 그대로 사용자 응답에 노출 |
| 주석 안의 `/*` | Kotlin 중첩 주석이 열려 뒤 코드가 통째로 주석 처리 |
| kotest `afterEach { clearMocks }` 로 스텁까지 삭제 | `Then` 블록 사이에 돌아 `verify` 가 빈 기록을 본다 |
| 크래시한 클라이언트가 "보는 중"으로 남음 | 알림이 영영 안 감. `viewers()` 를 `checkOnline()` 과 교집합 |
| `@Retryable` 을 `@EnableRetry` 없이 사용 | 조용히 안 돈다 |

---

## 14. 새 모듈 체크리스트

- [ ] `build.gradle.kts` 만 만들면 `settings.gradle.kts` 가 자동 등록한다. 의존성은 member 모듈 것을 복사해 시작
- [ ] QueryDSL 을 쓰면 `ksp(libs.dependency.querydsl.ksp)`
- [ ] 4계층 패키지(`api`/`application`/`domain`/`infrastructure`)를 먼저 만든다
- [ ] 다른 모듈이 써야 하는 인터페이스/이벤트는 `core` 에
- [ ] 저장소는 포트(domain) → 어댑터(infrastructure) 순
- [ ] Swagger 는 `{Domain}API` 인터페이스로 분리
- [ ] 스키마가 늘면 새 Flyway `V{n}__*.sql`
- [ ] i18n 키를 12개 번들 전부에 등록
- [ ] 단위 테스트(BehaviorSpec + MockK) + 통합 테스트(Testcontainers)

---

## 부록: 하지 말 것

| 금지 | 대신 |
|---|---|
| 엔티티를 `data class` 로 선언 | 일반 `class` |
| 엔티티를 컨트롤러에서 반환 | 응답 DTO 변환 |
| `@Cacheable` / `CacheManager` | `core.CacheProvider` |
| cascade/orphanRemoval 있는 엔티티에 `deleteAllInBatch` | `deleteAll` (고아 행 방지) |
| LAZY 연관을 가진 엔티티를 캐시 | 값 객체만 캐시 |
| 변경 가능한 값을 캐시 키로 쓰고 읽을 때 재검증 안 함 | 조회 후 키 일치 확인, 불일치면 DB 조회 |
| 조건 여러 개를 메서드명으로 이은 파생 쿼리 | QueryDSL (단일 조건·`@EntityGraph`·soft delete 필터는 허용) |
| application 이 `infrastructure` 직접 참조 | `domain` 의 포트 인터페이스 |
| 모듈이 다른 모듈을 직접 참조 | `core` 포트 / Kafka 이벤트 |
| 도메인 예외를 그대로 밖으로 전파 | `LanglezException` 으로 변환 |
| 예외 메시지에 한국어 문장 | i18n 메시지 키 |
| 회원 id 를 요청 본문으로 받기 | `@MemberId` |
| DB 트랜잭션 안에서 S3/외부 API 호출 | 트랜잭션 밖에서 먼저 처리 |
| `@Scheduled` 만 단독 사용 | `@DistributedLock` 병행 |
| `@TransactionalEventListener(AFTER_COMMIT)` 로 Outbox 기록 | `BEFORE_COMMIT` |
| 어노테이션 타깃 생략 (`@Schema`) | `@field:Schema` |
| 이미 적용된 Flyway V 파일 수정 | 새 V 파일 추가 |
| 고빈도 하트비트를 Kafka 로 | Redis 직결 |

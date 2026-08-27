# Langlez Backend — 도메인 모듈 규약

`module/*` 의 4계층(`api`/`application`/`domain`/`infrastructure`) 구현 규약. 모듈 구조·계층 의존 방향·네이밍·주석·코틀린 스타일·함정표는 저장소 루트 `CLAUDE.md` 에 있고, **여기서 그것들을 다시 적지 않는다.** 두 문서를 함께 지킨다.

`module/member` 가 기준 모듈이다. 문서와 코드가 어긋나면 member 모듈 코드가 정답이다.

---

## 1. 도메인 엔티티

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

## 2. 저장소: 포트와 어댑터

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
10. **카운터는 엔티티를 읽어 더하지 않고 DB 에서 더한다.** 좋아요 수, 안 읽은 수처럼 같은 행에 동시 요청이 몰리는 필드는 read-modify-write 로 증가가 유실된다. `@Modifying` JPQL UPDATE 로 원자화하고, 감소에는 0 아래로 못 가게 조건을 건다 (음수가 되면 되돌릴 방법이 없다).
    ```kotlin
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    fun decreaseLikeCount(id: Long)
    ```

---

## 3. 애플리케이션 계층

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

## 4. API 계층

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

### 실시간 (WebSocket / STOMP)

실시간 모듈은 각자 `config/{Domain}WebSocketConfiguration` 을 갖는다 (현재 `chat`, `wave`). 엔드포인트 등록과 모듈 고유의 부수 효과는 여기서 한다. 모바일 전용이라 SockJS 폴백은 두지 않는다.

**인증은 채널 공통, 인가는 공용 게이트 한 곳이다.** 모듈마다 `ChannelInterceptor` 를 달고 "내 접두사가 아니면 통과"시키던 구조는 실제로 뚫렸다 — 어느 접두사에도 안 걸리는 목적지를 아무도 검사하지 않아서, 별표 두 개짜리 구독 패턴과 인터셉터가 없던 `/topic/notification/{id}` 가 그대로 열려 있었다. **새 모듈이 인가를 빠뜨리면 열리는 게 아니라 닫혀야 한다.**

- **CONNECT** — `Authorization: Bearer` 헤더에서 토큰을 꺼내 `TokenBlacklist.isBlacklisted` 와 토큰 타입(`access`)을 확인하고, `accessor.user` 에 회원 id 를 심는다. 채널 전체에 한 번만 걸려 있다(현재 `ChatWebSocketConfiguration`). **소켓은 한 번 열리면 계속 살아 있어서 연결 시점에 못 막으면 그 뒤로 검사할 기회가 없다.**
- **SUBSCRIBE** — `common` 의 `WebSocketSubscriptionGate` 가 모든 구독을 받아, 등록된 `core.SubscriptionAuthorizer` 중 `supports` 가 참인 것에게 묻고 **하나도 없으면 거부한다.** 새 실시간 토픽을 만들면 모듈 `infrastructure` 에 `{Domain}SubscriptionAuthorizer` 를 `@Component` 로 추가하는 것이 전부다. 인터셉터를 새로 달지 않는다 — 다는 순간 기본 통과가 다시 생긴다.
  - 목적지는 **끝을 고정한 정규식**(`Regex("^/topic/chat/room/(\\d+)$")`)으로만 통과시킨다. 심플 브로커는 구독 목적지에 별표 와일드카드를 허용하므로, 방 번호 자리를 느슨하게 열면 전체 방을 한 번에 빨아간다. 숫자만 허용한다.
  - 게이트가 인증(`accessor.user`)까지 확인하므로 authorizer 는 순수 판정만 한다. 프레임워크 타입을 모른다.
- **모듈 인터셉터의 부수 효과는 게이트 뒤에 등록한다.** `registration.interceptors(gate, ...)` 순서를 지킨다. 앞에 두면 인가에 실패한 구독이 "보는 중"으로 기록된다.
- **UNSUBSCRIBE** — 프레임에 목적지가 없고 구독 id 만 온다. SUBSCRIBE 때 `구독 id → 목적지` 를 세션 속성에 남겨두고 여기서 꺼내 정리한다.
- **세션 종료** — 앱이 강제 종료되면 UNSUBSCRIBE 없이 소켓만 끊긴다. 인바운드 인터셉터는 그걸 못 보므로 `ApplicationListener<SessionDisconnectEvent>` 에서 정리한다. 안 하면 그 회원이 영원히 "보는 중"으로 남아 알림이 통째로 사라진다.

브로커는 인메모리(`enableSimpleBroker`)라 자기 JVM 에 붙은 세션에만 전달한다. 인스턴스가 여러 대면 다른 서버에 붙은 상대가 못 받는다. `RedisMessageBroadcaster` 가 pub/sub 으로 그 간극을 메우니, **서비스 코드는 `SimpMessagingTemplate` 이 아니라 `core.MessageBroadcaster` 포트를 쓴다.**

---

## 5. 비동기 / 스케줄링

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
- 스케줄러 클래스는 모듈 밖에서 부를 일이 없으면 `internal`.
- 튜닝 상수(`chunk`, `tries`, `threads`)는 부모의 `open val` 을 override 해 조정한다.

### 분산 락 (`@DistributedLock`)

`infra/redis` 의 Redisson 기반 AOP 어노테이션. 옵션은 `prefix`, `keys`(SpEL 배열), `leaseSecs`(0 이하면 자동 갱신), `waitMs`·`retries`(획득 재시도), `transactional`(락 획득 후 트랜잭션 시작), `throwOnFailure`(기본 `false` — 실패 시 조용히 스킵). 락 키는 파라미터에 `@LockKey` 를 붙이거나 `keys` 에 SpEL 을 준다.

```kotlin
@DistributedLock(prefix = "lock:profile-image:", leaseSecs = 5, retries = 20, waitMs = 100, transactional = true)
fun confirmAdditionalImage(@LockKey memberId: Long, fileUrl: String): ProfileImage

@DistributedLock(prefix = "lock:wave-join:", keys = ["#roomId"])
fun join(roomId: Long, memberId: Long)
```

- **스케줄러 전용이 아니다.** 여러 인스턴스에서 동시 실행되면 안 되는 쓰기(개수 제한 검사 후 삽입 등)에도 `transactional = true` 로 건다.
- **self-invocation 에 주의한다.** Spring AOP 는 프록시 기반이라 같은 클래스 안에서 `this.method()` 로 부르면 advice 가 아예 안 탄다. 락이 걸려야 하는 메서드는 별도 `@Component` 빈으로 분리한다. (`ProfileImageLocker` 가 `ProfileService` 와 분리된 이유)
- 스케줄러 중복 실행 방지처럼 **놓쳐도 다음 주기에 만회되는 경우**는 `throwOnFailure = false` 로 둔다.
- **체크+저장 원자화를 Lua(EVAL)로 직접 짜기 전에 `@DistributedLock` 을 먼저 고려한다.** Redisson 기본 코덱은 바이너리 직렬화라 Lua 인자가 원시 바이트로 넘어가고 `tonumber()`/`SISMEMBER` 비교가 조용히 깨진다. Lua 가 꼭 필요하면 `getScript(StringCodec.INSTANCE)` 로 코덱을 명시한다 — `DailyRateLimiter`(일일 카운터 INCR + EXPIRE)가 그 방식이다.

### Kafka 컨슈머

`api/{Domain}Consumer.kt` 에 둔다. 외부 메시지 계약과 내부 모델이 다르면 **컨슈머에서 변환하고 왜 변환하는지 주석을 남긴다.**

```kotlin
@KafkaListener(topics = ["chat-message-sent"], groupId = "notification")
fun onChatMessageSent(event: ChatMessageSentEvent) { ... }
```

---

## 6. 스키마 마이그레이션 (Flyway)

- 파일 위치: `infra/rdb/src/main/resources/migration/V{n}__*.sql`
- 운영·개발·테스트 **모두 `ddl-auto: validate`**. 통합테스트도 Flyway 를 타므로 마이그레이션 자체가 검증된다.
- **이미 적용된 V 파일은 절대 수정하지 않는다.** 체크섬 불일치로 기동이 실패한다. 고칠 게 있으면 새 V 파일을 만든다.
- **데이터가 이미 있는 테이블에 인덱스를 걸 때는 `create index concurrently` 를 검토한다.** 일반 `create index` 는 `SHARE` 락을 잡아 빌드가 끝날 때까지 그 테이블의 `INSERT`/`UPDATE`/`DELETE` 를 전부 세운다. 지금까지의 V 파일은 전부 같은 마이그레이션에서 방금 만든 빈 테이블에 걸어서 락이 0초였고, `V6` 도 운영 배포 전이라 그대로 뒀다. **행이 쌓인 뒤에 같은 패턴을 복사하면 배포 중 쓰기가 멈춘다.** `concurrently` 는 트랜잭션 안에서 못 돌므로 그 스크립트에 `-- flyway executeInTransaction=false` 를 붙여야 하고, 실패 시 `INVALID` 인덱스가 남아 수동 정리가 필요하다.
- **`@Column(nullable = false)` 를 새로 붙이면 기존 행 백필 마이그레이션이 반드시 따라와야 한다.** 안 하면 NULL 을 읽어 Kotlin non-null 프로퍼티에서 NPE 가 난다.
- 정렬·커서는 `created_at` 이 아니라 **id 시퀀스**나 도메인 시퀀스 기준. 인스턴스 간 시계 차이로 순서가 뒤집힌다.

---

## 7. i18n

**신규 메시지 키는 `common/src/main/resources/messages_*.properties` 12개 전부에 등록한다.**
(ko, ja, en, de, es, fr, pt, id, ru, vi, zh_CN, zh_TW)

`GlobalRestControllerAdvice` 는 키를 못 찾으면 **키 문자열을 그대로 응답 본문에 담아 클라이언트에 내보낸다.** 누락이 조용히 넘어간다.

확인:
```bash
for f in common/src/main/resources/messages_*.properties; do echo "$(basename $f) $(grep -c '^[a-z].*=' $f)"; done
```
전부 같은 수여야 한다.

**로케일 접미사가 없는 `common/src/main/resources/messages.properties` 를 지우지 않는다.** 비어 있어도 있어야 한다. Spring Boot 의 `MessageSourceAutoConfiguration` 은 `classpath*:{basename}.properties` 가 실제로 있는지만 보고 `messageSource` 빈 생성 여부를 정한다. 이 파일이 없으면 12개 번들이 다 있어도 빈이 안 생기고 `DelegatingMessageSource` 가 그 자리를 채워 **모든 키 조회가 실패한다** — 키 하나가 아니라 전 응답이 키 문자열로 나간다. 실제로 그 상태였다. 번역문은 로케일별 파일에만 두고 이 파일은 비워 둔다. 회귀 방지는 `app/api` 의 `MessageSourceAutoConfigurationTest` 가 한다.

---

## 8. 테스트

**모든 스펙은 `BehaviorSpec` 하나로 쓴다.** 계층에 따라 `DescribeSpec` 으로 갈아타지 않는다 — 이 저장소에 `DescribeSpec` 은 한 개도 없다. 무게중심은 단위 테스트(컨텍스트 없음 + MockK)에 두고, `@SpringBootTest` 는 부수 효과(DB·Outbox·롤백)를 확인해야 할 때만 만든다. E2E 는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`.

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
- 모듈에 첫 `@SpringBootTest` 를 넣을 때는 테스트 전용 진입점 `Test{Domain}Application.kt` 를 함께 만든다.
- **동시성 테스트는 일반 CRUD 테스트와 같은 클래스에 섞지 않는다.** 동시성 테스트가 남긴 행이 페이지네이션·카운트 검증을 오염시킨다. 파일을 나눈다.
- 여러 모듈을 가로지르는 E2E 는 `app/api/src/test/` 에 둔다. 모듈 하나짜리 컨텍스트로는 조립이 안 된다.

### 테스트 안티패턴

- **`Thread.sleep()` 금지.** 비동기 결과는 kotest `eventually(3.seconds) { ... }` 로 기다린다.
- private 메서드를 직접 테스트하지 않는다. 테스트 간 상태를 공유하지 않는다.
- `any()` 남발 금지. 의미가 걸린 인자는 `eq()` 로 구체값을 맞춘다.
- 한 `Then` 에 관련 없는 단언을 몰아넣지 않는다.
- **버그를 "예상 동작"으로 인코딩하지 않는다.** 500 이 나는 게 실은 버그인데 기대값을 500 으로 적어 통과시키는 식. 올바른 기대값을 적고 구현을 고친다.

### RED 를 진짜로 확인한다

테스트가 "통과해서" 넘어가지 말고 **먼저 실패하는 걸 눈으로 본다.** 이 저장소에서 relaxed mock 이 non-null 을 돌려주는 바람에 `OncePerRequestFilter` 가 아예 안 타고도 초록이 뜬 적이 있다. 의심되면 구현을 잠깐 되돌려 빨간불을 확인한다.

---

## 9. 새 모듈 체크리스트

- [ ] `build.gradle.kts` 를 만든다 (아래 템플릿). `settings.gradle.kts` 가 자동 등록한다
- [ ] **`app/api/build.gradle.kts` 에 `implementation(project(":module:<name>"))` 를 추가한다.** 빠뜨리면 앱에 아예 안 실린다
- [ ] 4계층 패키지(`api`/`application`/`domain`/`infrastructure`)를 먼저 만든다
- [ ] 다른 모듈이 써야 하는 인터페이스/이벤트는 `core` 에
- [ ] 저장소는 포트(domain) → 어댑터(infrastructure) 순
- [ ] Swagger 는 `{Domain}API` 인터페이스로 분리
- [ ] 스키마가 늘면 새 Flyway `V{n}__*.sql`
- [ ] i18n 키를 12개 번들 전부에 등록
- [ ] 단위 테스트(BehaviorSpec + MockK) + 통합 테스트(Testcontainers). 통합 테스트를 넣으면 `Test{Domain}Application.kt` 도

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.ksp)   // QueryDSL 을 쓸 때만
}

dependencies {
    implementation(project(":common"))
    implementation(project(":core"))
    implementation(project(":infra:rdb"))
    implementation(project(":infra:redis"))
    implementation(project(":infra:kafka"))

    ksp(libs.dependency.querydsl.ksp)   // QueryDSL 을 쓸 때만

    testImplementation(libs.test.kotest.spring)
    testImplementation(libs.test.springboot)
    testImplementation(libs.bundles.testcontainers)
}
```

`infra:*` 와 `common` 이 `api()` 로 노출하는 것(JPA, web, validation, swagger 등)은 다시 선언하지 않는다. kotest·mockk 는 루트 `build.gradle.kts` 가 모든 서브프로젝트에 자동으로 넣는다.

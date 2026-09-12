# module/chat — 모듈 규약

**역할**: 도메인 모듈 `chat` — 4계층(api / application / domain / infrastructure)
**경로**: `module/chat`
**의존 모듈**: `common`, `infra:mongo`, `infra:rdb`, `infra:redis`, `module:attachment-api`, `module:block-api`, `module:chat-api`, `module:member-api`, `module:member`

---

## 0. 경계 — 이 디렉터리 밖으로 나가지 않는다

**이 모듈 디렉터리(`module/chat`) 밖의 파일은 읽지도 고치지도 않는다.** 저장소 루트 `CLAUDE.md`, `module/CLAUDE.md`, 다른 모듈의 소스, `README.md` 전부 열지 않는다. 필요한 규약은 전부 이 문서에 옮겨 담았다 — **이 파일이 이 모듈의 유일한 규약 문서다.**

- 다른 모듈의 코드가 궁금하면 열지 말고, 그 모듈이 계약(`module/*-api`, `core`)으로 내준 인터페이스 시그니처만 보고 쓴다. 그마저도 이 문서에 적힌 범위를 넘어가면 **추측하지 말고 사용자에게 묻는다.**
- 루트의 `settings.gradle.kts`, `app/api/build.gradle.kts`, `infra/rdb` 의 Flyway 디렉터리처럼 **이 모듈 밖에 있는 파일을 고쳐야 하는 작업이면, 직접 하지 말고 무엇을 어떻게 바꿔야 하는지 사용자에게 보고한다.**
- 빌드·테스트 명령은 저장소 루트의 `./gradlew` 를 **실행**만 한다 (파일을 열람하지 않는다).

```bash
./gradlew :module:chat:test          # 이 모듈 테스트
./gradlew compileKotlin compileTestKotlin   # 컴파일만 빠르게 확인
```

로컬 인프라(Postgres·Redis·Kafka·MongoDB)는 **이미 저장소 루트에서 떠 있다. `./local-infra-start.sh` 를 실행하지 않는다** — 워크트리에서 띄우면 도커 볼륨 마운트가 그 워크트리로 잡혀, 워크트리를 지울 때 마운트가 stale 이 되고 Redis 가 모든 쓰기를 거부한다. `localhost` 로 붙으면 된다. Testcontainers 는 자기 컨테이너를 따로 띄우므로 무관하다.

## 1. 프로젝트 전제

Kotlin / Spring Boot 3.5.8 멀티모듈 백엔드. 언어교환 모바일 앱(iOS/Android)의 서버.

- **모바일 전용이다.** 쿠키를 쓰지 않는다. 토큰은 헤더로만 오간다.
- **1인 1기기 정책.** `X-Device-Id` 헤더가 필수고, 다른 기기로 로그인하면 기존 세션이 끊긴다.
- **`ddl-auto: validate`.** 스키마 변경은 Flyway 로만 한다. 엔티티와 마이그레이션이 어긋나면 기동 시점에 죽는다.
- **`open-in-view: false`.** 트랜잭션 밖에서 LAZY 연관을 만지면 터진다.
- **탈퇴 회원 데이터는 지우지 않는다.** 익명화도 안 한다. 재가입 후 같은 문제를 반복하는 회원 추적이 목적인 의도된 정책이다.
- **린트 단계가 없다.** ktlint/detekt 를 붙이지 않았으니 린트 태스크가 있다고 가정하지 않는다.
- **설정 파일을 이 모듈에 만들지 않는다.** `application.yml` 은 `app/api` 두 파일에만 있다. 기본값이 필요하면 `@Value`/`@ConfigurationProperties` 로 코드에 둔다. 단 `logback-spring.xml` 은 `common` 과 `app/api` 양쪽에 의도적으로 중복 존재한다 — 중복으로 보고 지우지 않는다.
- **보안에 직결되는 설정값에 `@Value("${key:fallback}")` 같은 조용한 기본값을 넣지 않는다.** 운영에서 프로퍼티가 빠져도 부팅해 문제를 숨긴다. 프로덕션 코드는 필수값으로 두고, 통합테스트가 `@SpringBootTest(properties = [...])` 로 명시적으로 넣는다.

## 2. 모듈 지도 (열어보지 말고 이 표만 본다)

```
core            소유자가 인프라인 순수 계약. 의존성 0
common          웹·보안·예외·필터·i18n 공용
infra/rdb       JPA + QueryDSL + Outbox 베이스 + Flyway
infra/redis     Redisson, 캐시 어댑터, 분산 락, pub/sub 브로드캐스터
infra/mongo     Mongo 리포지토리 스캔 + 인덱스 초기화
infra/kafka     프로듀서·컨슈머 설정, DLT
module/*        도메인 모듈 (api / application / domain / infrastructure 4계층)
module/*-api    계약 모듈. 그 도메인이 남에게 내주는 포트·이벤트만. 의존성 0
app/api         조립 + 실행
```

계약 배치:

| 모듈 | 담긴 것 |
|---|---|
| `core` | `CacheProvider`/`Cache`, `MessageBroadcaster`, `MessageDeduplicator`, `SubscriptionAuthorizer` |
| `module/member-api` | `MemberReader`(계정 정보 + 상태), `MemberWriter`(정지·해제), `PushTokenReader`, `OnlineTracker`, `Member{Created,HandleChanged,Withdrawn}Event` |
| `module/follow-api` | `FollowReader`, `MemberFollowedEvent` |
| `module/block-api` | `BlockReader`, `MemberBlockedEvent` |
| `module/lang-api` | `LanguageReader` (언어 프로필 조회 + 상호보완 후보 질의) |
| `module/matching-api` | `MatchingReader` |
| `module/moderation-api` | `ReportWriter` |
| `module/attachment-api` | `Storage` |
| `module/notification-api` | `Notificator` |
| `module/chat-api` | `ChatMessageSentEvent`, `ChatUserReportedEvent` |
| `module/echo-api` | `EchoPostLikedEvent`, `EchoCommentCreatedEvent` |

**모듈 간에는 서로를 직접 참조하지 않는다.** 상대 모듈의 `{도메인}-api` 계약(포트·이벤트)만 본다.

| 목적 | 수단 |
|---|---|
| 모듈 간 상태 변경 전파, 유실되면 안 되는 것 | **Kafka** (아웃박스 경유). 이벤트 DTO 는 발행 모듈의 `{도메인}-api` 에 |
| 응답을 기다려야 하는 조회 | **소유 모듈의 `{도메인}-api` 포트** |
| 접속 중인 사용자에게 실시간 전달 | **`core.MessageBroadcaster`** → Redis pub/sub → WebSocket |
| 고빈도 하트비트 | **Redis 직결** (Kafka 금지) |

**`*-api` 포트 호출은 원격 호출로 취급한다.** 곧 gRPC/HTTP 로 대체된다. 트랜잭션 안에 두면 DB 커넥션을 쥔 채 네트워크를 기다려 풀이 마르고, 롤백이 원격 쪽을 되돌리지 못한다. **포트 호출을 먼저 끝내고 결과만 트랜잭션에 넘긴다**(`TransactionTemplate.execute`). 목록 조회는 항목마다 부르지 말고 배치 메서드(`MemberReader.findProfileInfos`, `BlockReader.blockedAmong`)로 한 번에 묻는다. 그 대가로 판정과 저장 사이에 TOCTOU 창이 열리는데, **감수하기로 한 창은 KDoc 에 무엇이 어긋날 수 있는지 적는다.**

## 3. 네이밍

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
| 계약 포트 (읽기) | `{도메인}Reader` (`{도메인}-api`) | `MemberReader` |
| 계약 포트 (쓰기) | `{도메인}Writer` (`{도메인}-api`) | `MemberWriter`, `ReportWriter` |
| 계약 모듈의 조회 결과 DTO | `{...}Info` | `MemberReader.ProfileInfo` |
| 계약 모듈의 조작 결과 DTO | `{...}Result` | `Storage.PresignedResult` |

주입받는 의존성은 **짧은 관용 이름**을 쓴다. 타입명을 그대로 반복하지 않는다.

```kotlin
class MemberRepositoryImpl(
    private val jpa: MemberJpaRepository,   // Spring Data
    private val dsl: JPAQueryFactory,       // QueryDSL
    caches: CacheProvider,                  // 캐시
) : MemberRepository
```

관용 축약: `repo`, `jpa`, `dsl`, `caches`/`cache`, `tx`(TransactionTemplate), `mq`(MessageProducer), `publisher`(ApplicationEventPublisher), `mapper`(ObjectMapper), `service`. `memberRepository`, `memberJpaRepository` 처럼 클래스명을 통째로 복붙한 파라미터명은 쓰지 않는다.

## 4. 주석과 코틀린 스타일

**주석은 한국어로 쓰고 "무엇"이 아니라 "왜"를 남긴다.** 코드를 보면 아는 내용은 적지 않는다. 반드시 남겨야 하는 곳:

1. **누가 보면 버그로 오해할 코드** — 되돌리려는 시도를 막는다.
2. **의도적으로 생략한 것** — 트랜잭션, 락, 검증 등.
3. **성능/정합성 때문에 택한 구조** — KDoc 으로.
4. **외부 계약과 내부 모델이 어긋나는 지점.**

주의: 주석 안에 여는 블록주석 기호(슬래시+별표)가 들어가면(예: 토픽 패턴) Kotlin 중첩 주석이 열려 뒤 코드가 통째로 주석 처리된다. 표현을 바꿔 쓴다.

스타일:

- 들여쓰기 4칸, 최대 줄 길이 120자.
- `import` 와일드카드 금지. 단 `jakarta.persistence.*` 처럼 엔티티에서 다수를 쓰는 경우는 허용.
- enum 상수는 개별 import 해 짧게 쓴다. `import jakarta.persistence.EnumType.STRING` → `@Enumerated(STRING)`
- nullable 처리는 `?.let`, `?:` 우선. `!!` 는 테스트 외에 쓰지 않는다.
- 함수 인자가 3개를 넘으면 호출 시 **이름 붙인 인자**.
- 클래스 밖으로 나갈 필요 없는 건 `private`, 모듈 밖으로 나갈 필요 없는 건 `internal`.
- 버전은 반드시 `libs.*` 버전 카탈로그 별칭으로 참조한다. 하드코딩 금지.
- 코루틴을 쓰지 않는다. Java 21 가상 스레드로 간다.
- **Spring 플레이스홀더처럼 달러중괄호를 문자 그대로 남겨야 하는 곳은 백슬래시 대신 멀티-달러 문자열을 쓴다.**
  ```kotlin
  @Value($$"${storage.access-key}") accessKey: String
  ```

## 5. 반복해서 터진 함정

전부 "조용히 잘못되는" 종류라 테스트 없이는 못 찾는다.

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
| 로케일 접미사 없는 `messages.properties` 부재 | 자동설정 조건이 안 맞아 `messageSource` 빈이 아예 안 생김. **전 응답**이 키 문자열 |
| WebSocket 구독 인가를 모듈마다 "내 접두사 아니면 통과"로 | 아무도 안 맡는 목적지가 열린 채 남음. 기본 거부로 뒤집어야 한다 |
| 주석 안의 여는 블록주석 기호 | Kotlin 중첩 주석이 열려 뒤 코드가 통째로 주석 처리 |
| kotest `afterEach { clearMocks }` 로 스텁까지 삭제 | `Then` 블록 사이에 돌아 `verify` 가 빈 기록을 본다 |
| 크래시한 클라이언트가 "보는 중"으로 남음 | 알림이 영영 안 감. `viewers()` 를 `checkOnline()` 과 교집합 |
| `@Retryable` 을 `@EnableRetry` 없이 사용 | 조용히 안 돈다 |
| 프록시 대상 클래스의 `final` 메서드 | CGLIB 이 오버라이드를 못 해 필드가 빈 프록시에서 실행. `lateinit ... has not been initialized` |
| `create index concurrently` 를 열린 트랜잭션과 함께 | 기존 트랜잭션이 끝나기를 무한정 기다린다. 통합테스트가 3시간 반 멈춘 적이 있다 |
| `StringPath.search()`(pg_trgm+unaccent) 쓰며 `f_unaccent()` GIN 인덱스 없음 | 쿼리는 통과하지만 매번 Seq Scan |

## 6. 하지 말 것

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
| 모듈이 다른 모듈을 직접 참조 | `core` 포트 / `*-api` 계약 / Kafka 이벤트 |
| 도메인 예외를 그대로 밖으로 전파 | `LanglezException` 으로 변환 |
| 예외 메시지에 한국어 문장 | i18n 메시지 키 |
| 회원 id 를 요청 본문으로 받기 | `@MemberId` |
| DB 트랜잭션 안에서 `*-api` 포트 / S3 / 외부 API 호출 | 트랜잭션 밖에서 먼저 끝내고 결과만 넘긴다 |
| `@Scheduled` 만 단독 사용 | `@DistributedLock` 병행 |
| `@TransactionalEventListener(AFTER_COMMIT)` 로 Outbox 기록 | `BEFORE_COMMIT` |
| 어노테이션 타깃 생략 (`@Schema`) | `@field:Schema` |
| 이미 적용된 Flyway V 파일 수정 | 새 V 파일 추가 |
| 고빈도 하트비트를 Kafka 로 | Redis 직결 |
| `@DistributedLock` 메서드를 같은 클래스에서 호출 | 별도 `@Component` 빈으로 분리 |
| 하위 모듈에 `application.yml` 생성 | `app/api` 의 두 파일에 통합 (사용자에게 보고) |
| 테스트에서 `Thread.sleep()` | kotest `eventually` |

## 7. 4계층

```
module/chat/src/main/kotlin/com/langlez/chat/
├── api/                        # 외부 진입점 (HTTP, Kafka, 애플리케이션 이벤트)
│   ├── ChatAPI.kt               # Swagger 문서 전용 인터페이스
│   ├── ChatController.kt        # ChatAPI 구현, Spring MVC 매핑만
│   ├── ChatEventListener.kt     # @TransactionalEventListener → Outbox 기록
│   ├── request/                # 요청 DTO
│   └── response/               # 응답 DTO
├── application/                # 유스케이스 조합, 트랜잭션 경계
├── domain/                     # 엔티티 + 저장소 포트(인터페이스)
└── infrastructure/             # 포트의 구현(어댑터)
    ├── ChatRepositoryImpl.kt
    ├── jpa/                    # Spring Data 인터페이스만
    └── outbox/                 # Outbox 엔티티 + 스케줄러
```

계층 이름과 depth 를 임의로 바꾸지 않는다. **의존 방향은 한쪽으로만 흐른다.**

```
api ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` 은 다른 계층을 import 하지 않는다. 프레임워크 의존은 영속성/감사 애노테이션(`@Entity`, `@CreatedDate`, `AuditingEntityListener`)까지만. 웹/HTTP 타입(`HttpStatus`, `LanglezException`)은 넣지 않는다 — 불변식은 `require` 로 던지고 변환은 application 이 한다.
- `application` 은 `domain` 의 포트 인터페이스만 안다. `infrastructure` 구현 클래스를 직접 참조하지 않는다.
- `infrastructure` 가 `domain` 인터페이스를 구현하며 방향을 뒤집는다.

## 8. 도메인 엔티티

1. **`data class` 를 쓰지 않는다.** 일반 `class` + 주생성자 + 기본값. DTO 만 `data class` 다.
2. **PK 는 기본적으로 `@Id @GeneratedValue(IDENTITY) val id: Long = 0`.** 항상 `val`, 기본값 `0`. 식별관계는 예외 — 부모 PK 공유는 `@MapsId`, 복합키는 `@IdClass`.
3. **enum 은 반드시 `@Enumerated(STRING)`.** ordinal 저장 금지.
4. **enum 과 상수는 엔티티 안에 중첩한다.** `Member.Status`, `Member.HANDLE_REGEX`. 최상위로 빼지 않는다.
5. **비즈니스 규칙은 엔티티 메서드로.** 서비스에서 필드를 직접 조작하지 않고 `member.suspend()` 를 호출한다. 불변식은 메서드 안에서 `require`.
6. **`require` 의 메시지는 i18n 메시지 키다.** `require(...) { "member.handle.cooldown" }` — 사람이 읽는 문장을 넣지 않는다. 키 형식은 `{도메인}.{대상}.{사유}`.
7. **제약조건에 이름을 붙인다.** 유니크 `UNQ_{TABLE}_{COLUMN}`, 인덱스 `IDX_{TABLE}_{COLUMN}`.
8. **컬럼명이 프로퍼티명과 다르면 `@Column(name = ...)` 명시.**
9. **시각은 `Instant`.** `LocalDateTime`/`Date` 안 쓴다. 날짜면 `LocalDate`. 시각 인자는 `now: Instant = Instant.now()` 로 받아 테스트에서 주입 가능하게 한다.
10. **낙관적 락이 필요한 엔티티는 `@Version`.**
11. **감사 필드가 여러 개면 별도 엔티티로 분리한다.** (`@OneToOne(fetch = LAZY, cascade = [ALL], orphanRemoval = true)`)
12. **검증 규칙은 한 곳에서만 정의한다.** 정규식은 엔티티 companion 에 두고 요청 DTO 가 `@field:Pattern(regexp = Member.HANDLE_REGEX, ...)` 로 참조한다.

```kotlin
@Entity
@EntityListeners(AuditingEntityListener::class)
@Table(name = "members", uniqueConstraints = [UniqueConstraint("UNQ_MEMBER_HANDLE", ["handle"])])
class Member(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val email: String,
    @Column(length = 20) var handle: String = randomHandle(),
    @Enumerated(STRING) var status: Status = Status.CREATED,
    @Version var version: Long = 0,
) {
    fun changeHandle(newHandle: String, now: Instant = Instant.now()) {
        require(canChangeHandle(now)) { "member.handle.cooldown" }
        handle = newHandle
    }

    enum class Status { CREATED, ACTIVE, SUSPENDED, WITHDRAWN }
}
```

## 9. 저장소: 포트와 어댑터

**포트 (domain)** — 순수 인터페이스. 프레임워크 타입(`Page`, `Pageable`, `Optional`)을 노출하지 않는다. 페이징은 커서 기반으로 `size`, `cursor` 를 직접 받는다.

- **단건 조회는 `find` 로 오버로딩한다.** 파라미터 타입이 이미 의도를 말한다. 타입이 겹칠 때만 `findByEmail` 처럼 이름을 붙인다.
- **없으면 `null` 을 반환한다.** 예외 변환은 application 몫이다.
- 복수 조회는 `findAll`, 반환은 `List<T>`.

**어댑터 (infrastructure)**

1. **캐시는 `core.CacheProvider` 포트를 쓴다.** `@Cacheable`/`CacheManager` 는 쓰지 않는다 — self-invocation 에 취약하고 갱신 시점이 안 보인다.
2. **2단계 캐시 구조.** 보조 캐시는 **PK 만 문자열로** 저장하고 실제 엔티티는 한 곳에만 둔다. 복제하면 갱신 시 반드시 어긋난다.
3. **`updateCaches` / `evictCaches` 는 항상 대칭 쌍.**
4. **바뀔 수 있는 값을 캐시 키로 쓰면 읽을 때 반드시 재검증한다.**
   ```kotlin
   val id = handles.get<String>(handle)?.toLongOrNull()
   // handle 은 바뀔 수 있는 키다. 캐시로 찾은 회원의 handle 이 다르면 낡은 항목이다.
   val cached = id?.let(jpa::findByIdOrNull)?.takeIf { it.handle == handle }
   if (cached == null && id != null) handles.evict(handle)
   ```
5. **LAZY 연관을 가진 엔티티는 캐시하지 않는다.** detached 라 변경이 전파되지 않고, 오래된 `@Version` 을 되써서 `OptimisticLockException` 을 부른다. 값 객체만 캐시한다.
6. **동적·복합 조건 조회는 QueryDSL.** 파생 쿼리가 나은 예외: `@EntityGraph` 조회, 단일 조건 존재 확인, soft delete 필터가 붙은 단순 조회.
7. **QueryDSL Q타입은 별칭 import.** `import com.langlez.member.domain.QMember.Companion.member as QMember`
8. **컬렉션 인자는 빈 값을 먼저 걷어낸다.** `if (ids.isEmpty()) return emptyList()`. 중복은 `toSet()`.
9. **일괄 삭제는 조건부로 `deleteAllInBatch`.** 단 `cascade`/`orphanRemoval` 이 걸린 연관이 있으면 쓰면 안 된다 — 자식 행이 고아로 남는다.
10. **카운터는 엔티티를 읽어 더하지 않고 DB 에서 더한다.** QueryDSL UPDATE 로 원자화하고, 감소에는 0 아래로 못 가게 조건을 건다.
    ```kotlin
    dsl.update(QPost)
        .set(QPost.likeCount, QPost.likeCount.subtract(1L))
        .where(QPost.id.eq(id), QPost.likeCount.gt(0L))
        .execute()
    ```

## 10. 애플리케이션 계층

- **읽기 전용은 `@Transactional(readOnly = true)`, 쓰기는 `@Transactional`.** 클래스 레벨에 걸지 않고 메서드마다 명시한다.
- **일부러 트랜잭션을 걸지 않았다면 이유를 주석으로 남긴다.**
- **네트워크 I/O(S3, 외부 API, `*-api` 포트)는 DB 트랜잭션 안에 넣지 않는다.**
- **application 계층은 `LanglezException(HttpStatus, 메시지키)` 만 던진다.** 도메인의 `IllegalArgumentException` 은 `try/catch` 로 잡아 상태코드를 붙여 변환하고 원인 예외를 세 번째 인자로 넘긴다.
- **`findOrThrow` 는 private 헬퍼.** 키 타입별로 오버로딩한다.
- **유니크 제약 경합은 `@Retryable(retryFor = [DataIntegrityViolationException::class])`** 로 흡수한다.
- 상태 변경 후 저장은 `apply { }` + `also(repo::save)` 체인. 단일 표현식 함수는 `=` 본문.
- **실패해도 주 흐름을 막으면 안 되는 부수 효과는 `runCatching`.** 단 데이터 정합성이 걸린 곳엔 쓰지 않는다.
- 도메인 이벤트 DTO 는 발행 모듈의 `module/{domain}-api` 에 `data class` 로. 발행은 `ApplicationEventPublisher.publishEvent`.
- 수신 후 Outbox 기록은 **`@TransactionalEventListener(phase = BEFORE_COMMIT)`**. `AFTER_COMMIT` 은 이벤트만 남고 원본이 롤백되는 불일치를 만든다.

## 11. API 계층

**Swagger 문서와 컨트롤러를 분리한다. 이 프로젝트의 핵심 관례다.** 컨트롤러에 `@Operation`, `@Schema` 를 직접 붙이지 않는다.

```kotlin
@Tag(name = "Member", description = "회원 계정 관리 API")
interface MemberAPI {
    @Operation(summary = "핸들 변경", description = "15일 쿨다운 및 중복 검사가 있다.")
    fun patchHandle(memberId: Long, request: MemberUpdateHandleRequest): MemberMeResponse
}

@RestController
@RequestMapping("/api/v1/members")
class MemberController(private val service: MemberService) : MemberAPI {
    @PatchMapping("/me/handle")
    override fun patchHandle(
        @MemberId memberId: Long,
        @RequestBody @Valid request: MemberUpdateHandleRequest,
    ): MemberMeResponse = MemberMeResponse(service.updateHandle(memberId, request.handle))
}
```

- 경로는 `/api/v1/{복수형 도메인}`. 본인 리소스는 `/me` 하위.
- **인증된 사용자 ID 는 `@MemberId memberId: Long` 으로만 받는다.** 본문에서 받으면 사칭이 된다. `Principal`/`SecurityContextHolder` 를 컨트롤러에서 직접 뒤지지 않는다.
- 요청 바디는 `@RequestBody @Valid` 를 항상 함께. 본문 없는 응답은 `@ResponseStatus(HttpStatus.NO_CONTENT)`.
- **컨트롤러는 로직을 갖지 않는다.** 단순 조회는 `repo` 를 직접 주입받아 써도 된다.
- **엔티티를 그대로 반환하지 않는다.**
- **모든 방 단위·소유자 단위 접근은 권한 검사를 거친다(IDOR 방지).** WebSocket SUBSCRIBE 도 마찬가지다.

**DTO** — `data class`, 모든 프로퍼티 `val`. **어노테이션에 `@field:` 타깃을 명시한다**(생략하면 파라미터에 붙어 런타임에 무시될 수 있다). 엔티티 → 응답 변환은 **보조 생성자**로 한다 (별도 Mapper 나 `toResponse()` 확장함수를 만들지 않는다). 응답은 노출 범위별로 나눈다 — 본인용 `ChatMeResponse`, 타인용 `ChatPublicResponse`. 하나의 DTO 에 nullable 을 섞어 재사용하지 않는다.

**파일 업로드** — `attachment-api` 의 `Storage.presign` 으로 presigned URL 을 내주고 확정은 **key 로만** 받는다. 클라이언트가 준 URL 을 그대로 저장하면 외부 주소를 심을 수 있다.

## 12. 실시간 (WebSocket / STOMP)

실시간 모듈은 각자 `{Domain}WebSocketConfiguration` 을 **모듈 루트**에 갖는다. 4계층 중 어디도 아니므로 `config` 하위 패키지를 만들지 않는다. 모바일 전용이라 SockJS 폴백은 두지 않는다.

- **CONNECT** — `Authorization: Bearer` 에서 토큰을 꺼내 `TokenManager.isRevoked` 와 토큰 타입(`ACCESS`)을 확인하고 `accessor.user` 에 회원 id 를 심는다. 소켓은 한 번 열리면 계속 살아 있어 연결 시점에 못 막으면 그 뒤로 기회가 없다.
- **SUBSCRIBE** — `common` 의 `WebSocketSubscriptionGate` 가 모든 구독을 받아 `core.SubscriptionAuthorizer` 중 `supports` 가 참인 것에게 묻고 **하나도 없으면 거부한다.** 새 실시간 토픽을 만들면 모듈 `infrastructure` 에 `{Domain}SubscriptionAuthorizer` 를 `@Component` 로 추가하는 것이 전부다. **인터셉터를 새로 달지 않는다** — 다는 순간 기본 통과가 다시 생긴다.
  - 목적지는 **끝을 고정한 정규식**(`Regex("^/topic/chat/room/(\\d+)$")`)으로만 통과시킨다. 심플 브로커는 별표 와일드카드를 허용하므로 느슨하면 전체 방을 한 번에 빨아간다.
- **모듈 인터셉터의 부수 효과는 게이트 뒤에 등록한다.** 앞에 두면 인가 실패한 구독이 "보는 중"으로 기록된다.
- **UNSUBSCRIBE** — 프레임에 목적지가 없고 구독 id 만 온다. SUBSCRIBE 때 `구독 id → 목적지` 를 세션 속성에 남겨 꺼내 정리한다.
- **세션 종료** — 강제 종료는 UNSUBSCRIBE 없이 끊긴다. `ApplicationListener<SessionDisconnectEvent>` 에서 정리한다. 안 하면 영원히 "보는 중"으로 남아 알림이 사라진다.
- 브로커는 인메모리라 자기 JVM 세션에만 전달한다. **서비스 코드는 `SimpMessagingTemplate` 이 아니라 `core.MessageBroadcaster` 포트를 쓴다.**

## 13. 비동기 / 스케줄링

**Outbox** — DB 트랜잭션과 메시지 발행의 원자성이 필요하면 `infra:rdb` 의 `OutBox`, `OutBoxRepository`, `OutBoxProcessor` 를 상속한다.

```kotlin
@Entity
@Table(name = "member_event_outbox")
class MemberOutBox(domain: String, topic: String, payload: String, key: String? = null)
    : OutBox(domain, topic, payload, key)

@Component
internal class MemberOutBoxScheduler(repo: MemberOutBoxRepository) : OutBoxProcessor<MemberOutBox>(repo) {
    override val chunk = 1000

    @Scheduled(cron = "*/2 * * * * *")
    @DistributedLock(prefix = "lock:member-outbox")
    override fun send() = super.send()
}
```

- **`@Scheduled` 가 붙은 메서드에는 `@DistributedLock` 을 반드시 함께 건다.** prefix 는 `lock:{용도}`.
- 스케줄러 클래스는 모듈 밖에서 부를 일이 없으면 `internal`. 튜닝 상수는 부모의 `open val` 을 override.
- **`@DistributedLock` 은 스케줄러 전용이 아니다.** 동시 실행되면 안 되는 쓰기(개수 제한 검사 후 삽입 등)에도 `transactional = true` 로 건다. 옵션: `prefix`, `keys`(SpEL), `leaseSecs`, `waitMs`·`retries`, `transactional`, `throwOnFailure`(기본 `false`).
- **self-invocation 주의.** 같은 클래스 안에서 부르면 advice 가 안 탄다. 별도 `@Component` 로 분리한다.
- **프록시가 씌워진 클래스의 메서드는 `open` 이어야 한다.** `final` 이면 프록시에서 그대로 실행돼 `lateinit ... has not been initialized` 로 터진다. Kotlin 기본이 `final` 이라 놓치기 쉽다.
- **체크+저장 원자화를 Lua(EVAL)로 짜기 전에 `@DistributedLock` 을 먼저 고려한다.** Redisson 기본 코덱은 바이너리라 Lua 인자 비교가 조용히 깨진다. 꼭 필요하면 `getScript(StringCodec.INSTANCE)`.
- **Kafka 컨슈머는 `api/{Domain}Consumer.kt`.** 외부 계약과 내부 모델이 다르면 컨슈머에서 변환하고 이유를 주석으로 남긴다.

## 14. 스키마 마이그레이션과 i18n

**둘 다 이 모듈 밖의 파일이다. 직접 만들지 말고 사용자에게 내용과 위치를 보고한다.**

- Flyway: `infra/rdb/src/main/resources/migration/V{n}__*.sql`. **이미 적용된 V 파일은 절대 수정하지 않는다** — 체크섬 불일치로 기동이 실패한다. 새 V 파일을 만든다.
- 인덱스는 기본적으로 일반 `create index`. **`concurrently` 를 기본으로 삼지 마라** — 기존 트랜잭션이 끝나기를 기다리다 영영 안 끝난다(통합테스트가 3시간 반 멈춘 적이 있다). 어느 쪽이든 이유를 SQL 주석에 남긴다.
- **`@Column(nullable = false)` 를 새로 붙이면 기존 행 백필 마이그레이션이 반드시 따라온다.** 안 하면 NULL 을 읽어 NPE.
- 정렬·커서는 `created_at` 이 아니라 **id 시퀀스** 기준. 인스턴스 간 시계 차이로 순서가 뒤집힌다.
- i18n: 신규 메시지 키는 `common/src/main/resources/messages_*.properties` **12개 전부**(ko, ja, en, de, es, fr, pt, id, ru, vi, zh_CN, zh_TW)에 등록해야 한다. 누락되면 키 문자열이 그대로 사용자 응답에 나간다.

## 15. build.gradle.kts

```kotlin
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.springboot)
    alias(libs.plugins.spring.dependency.management)
    alias(libs.plugins.kotlin.jpa)
    alias(libs.plugins.kotlin.ksp)   // QueryDSL 을 쓸 때만
}
```

`infra:*` 와 `common` 이 `api()` 로 노출하는 것(JPA, web, validation, swagger 등)은 다시 선언하지 않는다. kotest·mockk 는 루트 `build.gradle.kts` 가 모든 서브프로젝트에 자동으로 넣는다. **소비하는 `*-api` 만 추가한다.** 새 의존을 추가할 때 그 모듈이 위 "모듈 지도" 에 없으면 추측하지 말고 사용자에게 묻는다.

## 검증은 테스트로 한다

**"빌드 통과"는 아무것도 증명하지 않는다.** 고치기 전에도 빌드는 통과했다.

1. **수정 전 코드에서 새 테스트가 빨간불인 것을 먼저 확인한다.** 실패 메시지를 보고에 적는다. 빨간불이 안 되면 그 테스트는 결함을 안 덮는 것이다.
2. **테스트가 프로덕션 경로를 타는지 본다.** 리터럴 SQL, 손으로 짠 대역, 내부 메서드 직접 호출은 실제 경로를 안 덮을 수 있다. Testcontainers 로 진짜 인프라를 태우는 쪽이 확실하다.
3. **동시성 결함은 실제 동시성으로 검증한다.** mockk 로는 못 잡는다.
4. **스키마·설정 변경은 런타임으로 확인한다.** 측정용 데이터를 넣었으면 **반드시 원복한다.**
5. **어노테이션 누락은 리플렉션으로 고정한다.** `@Scheduled`·`@DistributedLock` 이 빠져도 컴파일과 테스트가 통과한다.

### Kotest 규약

**모든 스펙은 `BehaviorSpec` 하나로 쓴다.** 이 저장소에 `DescribeSpec` 은 한 개도 없다. 무게중심은 단위 테스트(컨텍스트 없음 + MockK)에 두고, `@SpringBootTest` 는 부수 효과(DB·Outbox·롤백)를 확인해야 할 때만 만든다.

```kotlin
class MemberServiceTest : BehaviorSpec({
    val repo = mockk<MemberRepository>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = MemberService(repo, publisher)

    afterEach { clearMocks(repo, publisher, answers = false) }

    fun member(id: Long = 1L, status: Member.Status = Member.Status.ACTIVE) = Member(...)

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

- **Given/When/Then 설명은 한국어 서술형.**
- 픽스처는 **스펙 안의 로컬 함수**로. 별도 `TestFixture` 클래스를 만들지 않는다.
- `afterEach { clearMocks(..., answers = false) }` 로 호출 기록만 초기화. 스텁까지 지우면 `verify` 가 빈 기록을 본다.
- 단언은 kotest 매처(`shouldBe`, `shouldThrow`, `shouldHaveSize`). JUnit `assertEquals` 를 섞지 않는다.
- **"하지 않음"도 검증한다.** `verify(exactly = 0) { ... }`
- DB 는 **Testcontainers PostgreSQL**. H2 로 대체하지 않는다. 외부 인프라는 `@TestConfiguration` + `@Primary` + `mockk(relaxed = true)`.
- `SpringBootTest` 를 쓸 땐 `override fun extensions() = listOf(SpringExtension)`, 본문은 `init { }` 블록. 모듈에 첫 `@SpringBootTest` 를 넣을 때는 테스트 전용 진입점 `Test{Domain}Application.kt` 를 함께 만든다.
- **동시성 테스트는 일반 CRUD 테스트와 같은 클래스에 섞지 않는다.** 남긴 행이 페이지네이션·카운트 검증을 오염시킨다.
- `Thread.sleep()` 금지. 비동기 결과는 `eventually(3.seconds) { ... }`.
- **버그를 "예상 동작"으로 인코딩하지 않는다.** 올바른 기대값을 적고 구현을 고친다.

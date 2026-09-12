# module/follow-api — 모듈 규약

**역할**: 계약 모듈 — `follow` 도메인이 남에게 내주는 포트·이벤트만 (의존성 0)
**경로**: `module/follow-api`
**의존 모듈**: 없음 (의존성 0)

---

## 0. 경계 — 이 디렉터리 밖으로 나가지 않는다

**이 모듈 디렉터리(`module/follow-api`) 밖의 파일은 읽지도 고치지도 않는다.** 저장소 루트 `CLAUDE.md`, `module/CLAUDE.md`, 다른 모듈의 소스, `README.md` 전부 열지 않는다. 필요한 규약은 전부 이 문서에 옮겨 담았다 — **이 파일이 이 모듈의 유일한 규약 문서다.**

- 다른 모듈의 코드가 궁금하면 열지 말고, 그 모듈이 계약(`module/*-api`, `core`)으로 내준 인터페이스 시그니처만 보고 쓴다. 그마저도 이 문서에 적힌 범위를 넘어가면 **추측하지 말고 사용자에게 묻는다.**
- 루트의 `settings.gradle.kts`, `app/api/build.gradle.kts`, `infra/rdb` 의 Flyway 디렉터리처럼 **이 모듈 밖에 있는 파일을 고쳐야 하는 작업이면, 직접 하지 말고 무엇을 어떻게 바꿔야 하는지 사용자에게 보고한다.**
- 빌드·테스트 명령은 저장소 루트의 `./gradlew` 를 **실행**만 한다 (파일을 열람하지 않는다).

```bash
./gradlew :module:follow-api:test          # 이 모듈 테스트
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

## 7. 계약 모듈 규약

이 모듈은 **계약 모듈**이다. `follow` 도메인이 남에게 내주는 **포트 인터페이스와 이벤트 DTO 만** 담는다.

- **의존성 0.** Spring·JPA·Jackson 을 넣지 않는다. **JDK 타입만 쓴다.** 의존성이 0 이어야 어느 모듈이든 순환 없이 물 수 있다.
- 패키지는 `com.langlez.follow.contract`. 디렉터리는 `-api` 인데 패키지가 `contract` 인 건 도메인 모듈에 이미 `com.langlez.follow.api`(컨트롤러·DTO)가 있어서다. 같은 이름을 쓰면 두 모듈에 걸친 split package 가 된다.
- `build.gradle.kts` 에는 `kotlin.jvm` 플러그인만.
- **계약 모듈은 `app/api/build.gradle.kts` 에 등록하지 않는다.** 빈이 없는 인터페이스·DTO 뿐이라 소비 모듈이 `implementation` 으로 물면 런타임 클래스패스에 그대로 올라온다.
- 네이밍: 조회 포트 `{도메인}Reader`, 쓰기 포트 `{도메인}Writer`, 조회 결과 DTO `{...}Info`, 조작 결과 DTO `{...}Result`, 이벤트 `{...}Event`.
- 이벤트 DTO 와 결과 DTO 는 `data class`, 모든 프로퍼티 `val`.
- **구현은 여기 두지 않는다.** 구현체는 소유 도메인 모듈의 `infrastructure`(또는 `application`)에 있다.
- **포트 시그니처를 바꾸면 소비 모듈 전부가 깨진다.** 이 모듈은 지금은 같은 프로세스지만 곧 gRPC/HTTP 경계가 된다 — 목록 조회는 항목 단위가 아니라 **배치 메서드**(`findProfileInfos(ids)`, `blockedAmong(ids)`)로 설계한다.
- **소유자 판단 기준**: 그 계약을 구현하고 데이터를 소유하는 게 도메인 모듈이면 `{도메인}-api`, 인프라면 `core`. 애매하면 `core`.

시그니처를 바꿀 일이 생기면 **소비 모듈이 어디인지 이 문서의 모듈 지도로 확인하고, 그 모듈 파일을 직접 열지 말고 사용자에게 영향 범위를 보고한다.**

### KDoc

포트 메서드에는 KDoc 을 단다. 특히 **감수하기로 한 정합성 창(TOCTOU)** — 판정과 저장 사이에 무엇이 어긋날 수 있는지 — 을 적는다. 계약만 보고 쓰는 쪽이 그 창을 모르면 잘못된 가정을 한다.

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

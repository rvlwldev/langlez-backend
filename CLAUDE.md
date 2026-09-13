# Langlez Backend

Kotlin / Spring Boot 3.5.8 멀티모듈 백엔드. 언어교환 모바일 앱(iOS/Android)의 서버.

`module/member` 가 이 프로젝트의 **기준 모듈(reference module)** 이다. 새 모듈을 만들거나 기존 모듈을 고칠 때 member 모듈의 구조와 관례를 그대로 따른다. 이 문서는 member 모듈의 실제 코드에서 역으로 추출했고, **문서와 코드가 어긋나면 member 모듈 코드가 정답**이다.

규약은 이 파일 하나에 모여 있다. 하위 디렉터리에 별도 `CLAUDE.md` 를 만들지 않는다 — 모듈마다 두면 같은 규칙이 31곳에서 따로 낡는다.

현황과 남은 작업은 `README.md` 를 본다.

---

## 0. 시작 전에 알아야 할 것

- **모바일 전용이다.** 쿠키를 쓰지 않는다. 토큰은 헤더로만 오간다.
- **1인 1기기 정책.** `X-Device-Id` 헤더가 필수고, 다른 기기로 로그인하면 기존 세션이 끊긴다.
- **`ddl-auto: validate`.** 스키마 변경은 Flyway 로만 한다. 엔티티와 마이그레이션이 어긋나면 기동 시점에 죽는다.
- **`open-in-view: false`.** 트랜잭션 밖에서 LAZY 연관을 만지면 터진다.
- **탈퇴 회원 데이터는 지우지 않는다.** 익명화도 안 한다. 재가입 후 같은 문제를 반복하는 회원 추적이 목적인 의도된 정책이다.

### 검증

```bash
./local-infra-start.sh                               # 로컬 인프라 (Postgres·Redis·Kafka·MongoDB·모니터링)
./gradlew build                                      # 전체 빌드 + 테스트. Testcontainers 를 쓰므로 Docker 필요
./gradlew :app:api:bootRun                           # 앱 실행. 인프라 기동이 선행돼야 한다
./gradlew :module:<name>:test --tests "*MemberTest"  # 모듈별 / 단일 클래스 테스트
./gradlew compileKotlin compileTestKotlin            # 컴파일만 빠르게 확인
```

**린트 단계가 없다.** ktlint/detekt 를 붙이지 않았으니 린트 태스크가 있다고 가정하지 않는다.

---

## 1. 모듈 구조

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

`settings.gradle.kts` 가 `infra/`·`module/` 하위를 자동 스캔한다. `build.gradle.kts` 만 만들면 서브프로젝트로 등록된다.

**등록만으로는 앱이 그 모듈을 로드하지 않는다.** `app/api/build.gradle.kts` 에 `implementation(project(":module:<name>"))` 을 직접 추가해야 한다. 빠뜨려도 컴파일과 모듈 단위 테스트는 통과하므로 조용히 넘어간다. **계약 모듈(`module/*-api`)은 예외로 등록하지 않는다** — 빈이 없는 인터페이스·DTO 뿐이라, 소비 모듈이 `implementation` 으로 물면 런타임 클래스패스에 그대로 올라온다.

### `module/` 아래 두 종류

| | 담는 것 | 의존성 | 예 |
|---|---|---|---|
| 도메인 모듈 `module/{name}` | 엔티티·서비스·컨트롤러. 4계층 | common·infra·필요한 `*-api` | `module/member` |
| 계약 모듈 `module/{name}-api` | 그 도메인이 남에게 내주는 포트 인터페이스와 이벤트 DTO | **0** | `module/member-api` |

**어디에 둘지의 판단 기준은 소유자다.** 그 계약을 구현하고 그 데이터를 소유하는 게 도메인 모듈이면 `{도메인}-api`, 인프라면 `core`.

- **`{도메인}-api`** — 도메인이 구현하는 조회 포트(`MemberReader`), 도메인이 발행하는 이벤트 DTO(`MemberCreatedEvent`).
- **`core`** — `infra/*` 가 구현하는 포트(`CacheProvider`, `MessageBroadcaster`, `MessageDeduplicator`), 그리고 방향이 뒤집힌 것(`SubscriptionAuthorizer` 는 `common` 이 소비하고 도메인들이 구현한다 — 어느 도메인의 것도 아니다).
- **애매하면 `core`.** 소유자가 하나로 정해지지 않는 계약을 억지로 도메인에 밀어 넣으면 그 도메인을 안 쓰는 모듈까지 끌려온다.

계약 모듈의 패키지는 `com.langlez.{domain}.contract` 다. 디렉터리는 `-api` 인데 패키지가 `contract` 인 건 **도메인 모듈에 이미 `com.langlez.{domain}.api`(컨트롤러·DTO)가 있어서**다. 같은 이름을 쓰면 두 모듈에 걸친 split package 가 된다.

**계약 모듈에 Spring·JPA 를 넣지 않는다.** JDK 타입만 쓴다. 의존성이 0 이어야 어느 모듈이든 순환 없이 물 수 있다.

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

`{Domain}WebSocketConfiguration` 처럼 4계층 어디도 아닌 설정 클래스는 **모듈 루트**에 둔다. `config` 하위 패키지를 만들지 않는다.

**`module/matching` 은 4계층의 유일한 예외다.** `api`/`application` 만 갖고 `domain`·`infrastructure` 가 없다.
자기 테이블이 하나도 없는 **조합 모듈**이라 `domain` 에 넣을 엔티티도, `infrastructure` 가 구현할 포트도 없다.
계층을 억지로 채우면 빈 패키지와 통과 전용 인터페이스만 남는다. 데이터를 갖게 되면 그때 만든다.
**새 모듈에 이 예외를 복사하지 마라** — 판단 기준은 "자기 소유 데이터가 있는가"다.

**의존 방향은 한쪽으로만 흐른다.**

```
api ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` 은 다른 계층을 import 하지 않는다. 프레임워크 의존은 영속성/감사 애노테이션(`@Entity`, `@CreatedDate`, `AuditingEntityListener`)까지만. 웹/HTTP 타입(`HttpStatus`, `LanglezException`)은 넣지 않는다 — 불변식은 `require` 로 던지고 변환은 application 이 한다.
- `application` 은 `domain` 의 포트 인터페이스만 안다. `infrastructure` 구현 클래스를 직접 참조하지 않는다.
- `infrastructure` 가 `domain` 인터페이스를 구현하며 방향을 뒤집는다.
- 모듈 간에는 서로를 직접 참조하지 않는다. 상대 모듈의 `{도메인}-api` 계약(포트·이벤트)만 본다.

### 모듈 간 통신 수단 선택

| 목적 | 수단 |
|---|---|
| 모듈 간 상태 변경 전파, 유실되면 안 되는 것 | **Kafka** (아웃박스 경유). 이벤트 DTO 는 발행 모듈의 `{도메인}-api` 에 |
| 응답을 기다려야 하는 조회 | **소유 모듈의 `{도메인}-api` 포트** |
| 접속 중인 사용자에게 실시간 전달 | **`core.MessageBroadcaster`** → Redis pub/sub → WebSocket |
| 고빈도 하트비트 | **Redis 직결** (Kafka 금지) |

고빈도·저가치 신호를 브로커에 태우면 비용만 든다. 접속 핑(5초 간격)이 그랬다 — 브로커 왕복에 handle→id 조회까지 붙어 있었다. 지금은 `MemberPingController` 가 레디스 버킷에 바로 쓴다.

**`*-api` 포트 호출은 원격 호출로 취급한다.** 지금은 같은 프로세스·같은 DB 라 트랜잭션 안에서 불러도 돌지만, 이 포트들은 곧 gRPC/HTTP 로 대체된다. 트랜잭션 안에 두면 그때 셋이 한꺼번에 터진다 — DB 커넥션을 쥔 채 네트워크를 기다려 풀이 마르고, 스냅샷 일관성은 어차피 사라지며, 롤백이 원격 쪽을 되돌리지 못한다. 셋 다 컴파일과 테스트를 통과하고 런타임에만 어긋난다. 그러니 **포트 호출을 먼저 끝내고 결과만 트랜잭션에 넘긴다**(`TransactionTemplate.execute`). 목록에 붙이는 조회는 항목마다 부르지 말고 배치 메서드(`MemberReader.findProfileInfos`, `BlockReader.blockedAmong`)로 한 번에 묻는다. 그 대가로 판정과 저장 사이에 TOCTOU 창이 열리는데, **감수하기로 한 창은 KDoc 에 무엇이 어긋날 수 있는지 적는다.**

현재 계약 배치:

| 모듈 | 담긴 것 |
|---|---|
| `core` | `CacheProvider`/`Cache`, `MessageBroadcaster`, `MessageDeduplicator`, `SubscriptionAuthorizer` |
| `module/member-api` | `MemberReader`(계정 정보 + 상태), `MemberWriter`(정지·해제), `PushTokenReader`, `OnlineTracker`, `Member{Created,HandleChanged,Withdrawn}Event` |
| `module/follow-api` | `FollowReader`, `MemberFollowedEvent` |
| `module/block-api` | `BlockReader`, `MemberBlockedEvent` |
| `module/lang-api` | `LanguageReader` (언어 프로필 조회 + 상호보완 후보 질의) |
| `module/matching-api` | `MatchingReader` (아직 소비자 없음 — 추천 소유가 matching 이라는 사실을 계약으로 못 박아 둔 것) |
| `module/moderation-api` | `ReportWriter` (아직 소비자 없음 — 신고 소유가 moderation 이라는 사실을 계약으로 못 박아 둔 것) |
| `module/attachment-api` | `Storage` |
| `module/notification-api` | `Notificator` |
| `module/chat-api` | `ChatMessageSentEvent`, `ChatUserReportedEvent` |
| `module/echo-api` | `EchoPostLikedEvent`, `EchoCommentCreatedEvent` |

**`common` 은 `module/member-api` 를 의존한다.** `JwtAuthenticationFilter` 가 매 요청 계정 상태를 보기 때문이다. 이 의존은 원래도 있었고 `core` 라는 이름 뒤에 가려져 있었을 뿐이다. 계약 모듈이 의존성 0 이라 순환은 생기지 않는다.

### 저장소 분담

| 데이터 | 저장소 | 이유 |
|---|---|---|
| 회원·프로필·방·참여자·아웃박스 | PostgreSQL | 조인·트랜잭션 필요, 유한 증가 |
| 채팅 메시지 본문 + 첨부 | MongoDB | 무한 증가, 첨부 임베드로 조회 1회 |
| 접속·화면 상태·분산 락·캐시·wave 채팅 | Redis | 휘발성·고빈도 |

### 설정과 로깅

**설정은 `app/api/src/main/resources/` 두 파일에만 둔다.** `application.yml`(기본값이자 로컬용, `docker/` 인프라 설정과 짝을 맞춘다. 여기 든 로컬 dummy 시크릿은 운영에서 안 쓰이므로 커밋해도 된다)과 `application-production.yml`(운영용, 민감값은 전부 `${ENV_VAR}` 주입). `module/*`, `infra/*`, `common/*` 에 `application.yml` 을 만들지 않는다 — 기본값이 필요하면 `@Value`/`@ConfigurationProperties` 로 코드에 둔다.

**`logback-spring.xml` 은 예외다 — `app/api` 와 `common` 양쪽에 둔다.** `module/*`·`infra/*` 는 `app/api` 를 의존하지 않아 단독 테스트(`./gradlew :module:<name>:test`)에서는 `app/api` 의 logback 이 클래스패스에 없다. `common` 것을 지우면 그 실행 경로가 아무 logback 설정 없이(스프링 부트 기본값으로) 돈다. 둘 다 살아있는 파일이니 "중복"으로 보고 하나를 지우지 않는다.

**보안에 직결되는 설정값에 `@Value("${key:fallback}")` 같은 조용한 기본값을 넣지 않는다.** 운영에서 프로퍼티가 빠져도 기본값으로 부팅해 문제를 숨긴다. 프로덕션 코드는 필수값으로 두고, 부분 컨텍스트만 띄우는 통합테스트가 `@SpringBootTest(properties = [...])` 로 명시적으로 넣는다.

**`@Enable*` 는 그걸 켜야 통과하는 테스트를 가진 모듈에 둔다.** 아무 모듈 테스트도 요구하지 않으면 `app/api`. 그래서 `EnableJpaRepositories`/`EnableJpaAuditing` 은 `infra/rdb`, `EnableKafka` 는 `infra/kafka`, `EnableWebSecurity` 는 `common`, `EnableWebSocketMessageBroker` 는 `module/chat`, `EnableRetry` 는 `module/member`(핸들 충돌 재시도가 member 의 도메인 동작이다), `EnableScheduling`/`EnableAsync` 만 `app/api` 에 있다. 스케줄링을 모듈로 내리면 모듈 단독 통합테스트마다 아웃박스 폴러가 2초 크론으로 테스트 DB 를 긁는다 — 스케줄러 계약은 리플렉션 테스트가 고정하므로 얻는 것도 없다.

쿼리 로깅은 `common/.../logger/PerformanceLogger` 한 곳을 통해 나간다 (현재는 P6Spy(RDB)만). 임계값은 `logger.rdb/mongo/redis.{log-threshold-ms, warn-threshold-ms}`, **`warn-threshold` 이상만 `WARN` 이고 나머지는 `DEBUG`** — 일반 쿼리 로그를 INFO 로 올리면 콘솔이 스케줄러 로그로 덮인다. 로그 파일은 `APP_LOG_PATH` 기본값이 `build/test-logs` 라 테스트가 소스 옆을 더럽히지 않고, `bootRun` 만 `app/log/langlez-server/logs` 를 주입해 Promtail 이 수집한다.

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
| 계약 포트 (읽기) | `{도메인}Reader` (`{도메인}-api`) | `MemberReader` |
| 계약 포트 (쓰기) | `{도메인}Writer` (`{도메인}-api`) | `MemberWriter`, `ReportWriter` |
| 계약 모듈의 조회 결과 DTO | `{...}Info` | `MemberReader.ProfileInfo`, `FollowReader.CountInfo` |
| 계약 모듈의 조작 결과 DTO | `{...}Result` | `Storage.PresignedResult` |
| DB 제약 | 유니크 `UNQ_{TABLE}_{COLUMN}`, 인덱스 `IDX_{TABLE}_{COLUMN}` | `UNQ_MEMBER_HANDLE` |
| i18n 키 | `{도메인}.{대상}.{사유}` (점 구분, 각 마디 kebab-case) | `member.handle.cooldown` |

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

## 3. 주석

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

## 4. 코틀린 스타일

- 들여쓰기 4칸, 최대 줄 길이 120자.
- `import` 와일드카드 금지. 단 `jakarta.persistence.*` 처럼 엔티티에서 다수를 쓰는 경우는 허용.
- enum 상수는 개별 import 해 짧게 쓴다. `import jakarta.persistence.EnumType.STRING` → `@Enumerated(STRING)`
- QueryDSL Q타입은 별칭 import. `import com.langlez.member.domain.QMember.Companion.member as QMember`
- nullable 처리는 `?.let`, `?:` 우선. `!!` 는 테스트 외에 쓰지 않는다.
- 함수 인자가 3개를 넘으면 호출 시 **이름 붙인 인자**.
- 단일 표현식 함수는 `=` 본문. 상태 변경 후 저장은 `apply { } ` + `also(repo::save)` 체인.
- 클래스 밖으로 나갈 필요 없는 건 `private`, 모듈 밖으로 나갈 필요 없는 건 `internal`.
- 버전은 반드시 `libs.*` 버전 카탈로그 별칭으로 참조한다. 하드코딩 금지.
- 코루틴을 쓰지 않는다. Java 21 가상 스레드로 간다.
- **`${...}` 를 문자 그대로 남겨야 하는 곳(Spring 플레이스홀더)은 백슬래시 대신 멀티-달러 문자열을 쓴다.**
  ```kotlin
  @Value($$"${storage.access-key}") accessKey: String
  ```

---

## 5. 도메인 엔티티

`Member.kt` 가 표준형이다.

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
3. **enum 은 반드시 `@Enumerated(STRING)`.** ordinal 저장 금지. **enum 과 상수는 엔티티 안에 중첩한다** (`Member.Status`, `Member.HANDLE_REGEX`). 최상위로 빼지 않는다.
4. **비즈니스 규칙은 엔티티 메서드로.** 서비스에서 `member.status = SUSPENDED` 처럼 필드를 직접 조작하지 않고 `member.suspend()` 를 호출한다. 불변식은 메서드 안에서 `require` 로 지킨다.
5. **`require` 의 메시지는 i18n 메시지 키다.** 사람이 읽는 문장을 넣지 않는다.
6. **제약조건에 이름을 붙인다.** 이름 없는 제약은 DB 에서 추적이 안 된다.
7. **컬럼명이 프로퍼티명과 다르면 `@Column(name = ...)` 을 명시한다.** (`provider` → `provider_type`)
8. **시각은 `Instant`.** `LocalDateTime`/`Date` 안 쓴다. 시각이 아니라 **날짜**면 `LocalDate`(`Member.birthDay`). 시각 인자는 `now: Instant = Instant.now()` 로 받아 테스트에서 주입 가능하게 한다.
9. **낙관적 락이 필요한 엔티티는 `@Version`.**
10. **감사 필드가 여러 개면 별도 엔티티로 분리한다.** (`MemberAudit`, `@OneToOne(fetch = LAZY, cascade = [ALL], orphanRemoval = true)`)

**검증 규칙은 한 곳에서만 정의한다.** `HANDLE_REGEX` 는 엔티티 companion 에 있고 요청 DTO 가 그걸 참조한다. 정규식을 두 군데 적지 않는다.

```kotlin
@field:Pattern(regexp = Member.HANDLE_REGEX, message = "member.handle.invalid")
val handle: String,
```

---

## 6. 저장소: 포트와 어댑터

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
}
```

- **단건 조회는 `find` 로 오버로딩한다.** `findById`, `findByHandle` 처럼 이름을 늘리지 않는다 — 파라미터 타입이 이미 의도를 말한다. 타입이 겹쳐 구분이 안 될 때만 `findByEmail` 처럼 이름을 붙인다.
- **없으면 `null` 을 반환한다.** 예외를 던지지 않는다. 예외 변환은 application 계층 몫이다.
- 복수 조회는 `findAll`, 반환은 `List<T>`.

### 어댑터 (infrastructure)

1. **캐시는 `core.CacheProvider` 포트를 쓴다.** Spring 의 `@Cacheable`/`CacheManager` 는 쓰지 않는다 — 어노테이션 기반 캐시는 self-invocation 에 취약하고 갱신 시점이 코드에 안 보인다. (Spring `Cache` 에 multi-get/multi-set 이 없어 컬렉션 조회가 건당 왕복으로 쪼개지는 것도 이유였다.)
2. **2단계 캐시 구조.** 보조 캐시(`member-handle`, `member-email`, `member-provider`)는 **PK 만 문자열로** 저장하고, 실제 엔티티는 `member` 캐시 한 곳에만 둔다. 엔티티를 여러 캐시에 복제하면 갱신 시 반드시 어긋난다.
3. **`updateCaches` / `evictCaches` 는 항상 대칭 쌍.**
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
7. **컬렉션 인자는 빈 값을 먼저 걷어낸다.** `if (ids.isEmpty()) return emptyList()` — 빈 `IN ()` 쿼리를 막는다. 중복은 `toSet()` 으로 제거.
8. **일괄 삭제는 조건부로 `deleteAllInBatch`.** `deleteAll` 은 건수만큼 단건 DELETE 라 느리다. 다만 `deleteAllInBatch` 는 **영속성 컨텍스트를 우회하므로 `cascade`/`orphanRemoval` 이 걸린 연관이 있으면 쓰면 안 된다** — 자식 행이 고아로 남는다. (`Member` 는 `audit` 에 `cascade = [ALL], orphanRemoval = true` 가 있어 `deleteAll` 을 쓴다.)
9. **카운터는 엔티티를 읽어 더하지 않고 DB 에서 더한다.** 좋아요 수, 안 읽은 수처럼 같은 행에 동시 요청이 몰리는 필드는 read-modify-write 로 증가가 유실된다. QueryDSL UPDATE(`dsl.update`) 로 원자화하고, 감소에는 0 아래로 못 가게 조건을 건다 (음수가 되면 되돌릴 방법이 없다).
   ```kotlin
   dsl.update(QPost)
       .set(QPost.likeCount, QPost.likeCount.subtract(1L))
       .where(QPost.id.eq(id), QPost.likeCount.gt(0L))
       .execute()
   ```
10. **`StringPath.search()`(`infra/rdb`, pg_trgm+unaccent)를 쓰면 그 컬럼에 `f_unaccent()` GIN 인덱스를 함께 건다.** 안 걸면 쿼리는 통과하지만 매번 Seq Scan 이다. 컬럼과 검색어 양쪽이 같은 `f_unaccent()` 를 거쳐야 인덱스를 탄다.

---

## 7. 애플리케이션 계층

```kotlin
@Service
class MemberService(
    private val repo: MemberRepository,
    private val publisher: ApplicationEventPublisher,
) {

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
- **네트워크 I/O(S3, 외부 API, `*-api` 포트)는 DB 트랜잭션 안에 넣지 않는다.** 커넥션을 잡은 채 외부를 기다리면 풀이 마른다. 먼저 끝내고 결과만 넘긴다.

### 예외

- **application 계층은 `LanglezException(HttpStatus, 메시지키)` 만 던진다.** 메시지 자리에는 i18n 키.
- **도메인의 `IllegalArgumentException` 을 그대로 흘리지 않는다.** `try/catch` 로 잡아 상태코드를 붙여 변환하고, 원인 예외를 세 번째 인자로 넘겨 스택을 보존한다.
- **`findOrThrow` 는 private 헬퍼.** 키 타입별로 오버로딩해 중복 `?: throw` 를 없앤다.
- **유니크 제약 경합은 `@Retryable(retryFor = [DataIntegrityViolationException::class])`** 로 흡수한다 (랜덤 handle 충돌 등). 같은 행이 이미 있어 재시도가 무의미한 경우(중복 신고 등)에는 걸지 않는다.
- **실패해도 주 흐름을 막으면 안 되는 부수 효과는 `runCatching`.** `.apply { runCatching { tracker.toOnline(id) } }` — 온라인 표시 실패로 가입을 실패시키지 않는다. 단, 삼켜도 되는 실패에만. 데이터 정합성이 걸린 곳엔 쓰지 않는다.

### 이벤트

- 도메인 이벤트 DTO 는 발행 모듈의 계약 모듈 `module/{domain}-api` (`com.langlez.{domain}.contract`) 에 `data class` 로 둔다.
- 발행은 `ApplicationEventPublisher.publishEvent`.
- 수신 후 Outbox 기록은 **`@TransactionalEventListener(phase = BEFORE_COMMIT)`**. 원 트랜잭션이 아직 열려 있어 Outbox insert 가 같은 트랜잭션에 묶이고 롤백 시 함께 사라진다. `AFTER_COMMIT` 을 쓰면 이벤트만 남고 원본이 롤백되는 불일치가 생긴다.

---

## 8. API 계층

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
- 요청 바디는 `@RequestBody @Valid` 를 항상 함께. 본문 없는 응답은 `@ResponseStatus(HttpStatus.NO_CONTENT)`.
- **컨트롤러는 로직을 갖지 않는다.** 단순 조회는 서비스를 거치지 않고 `repo` 를 직접 주입받아 써도 된다.
- **엔티티를 그대로 반환하지 않는다.** 항상 응답 DTO 로 변환한다.
- **모든 방 단위·소유자 단위 접근은 권한 검사를 거친다(IDOR 방지).** 인증만 통과했다고 남의 리소스에 닿으면 안 된다. WebSocket SUBSCRIBE 도 마찬가지다.
- **검증을 `x != null && x != y` 꼴로 쓰지 않는다.** 값을 빼기만 하면 검증이 통째로 우회된다(fail-open). 없으면 거부다.

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

`attachment-api` 의 `Storage.presign` 으로 presigned URL 을 내주고, 확정은 **key 로만** 받는다. **클라이언트가 준 URL 을 그대로 저장하면 외부 주소를 심을 수 있고 presigned 서명이 노출된다.** (`module/chat`, `module/profile`, `module/echo` 가 같은 패턴)

### 실시간 (WebSocket / STOMP)

실시간 모듈은 각자 `{Domain}WebSocketConfiguration` 을 모듈 루트에 갖는다 (현재 `chat`, `wave`). 엔드포인트 등록과 모듈 고유의 부수 효과는 여기서 한다. 모바일 전용이라 SockJS 폴백은 두지 않는다. `@EnableWebSocketMessageBroker` 는 `chat` 한 곳에만 있다 — 브로커 설정을 통째로 덮으므로 다시 붙이지 않는다.

**인증은 채널 공통, 인가는 공용 게이트 한 곳이다.** 모듈마다 `ChannelInterceptor` 를 달고 "내 접두사가 아니면 통과"시키던 구조는 실제로 뚫렸다 — 어느 접두사에도 안 걸리는 목적지를 아무도 검사하지 않아서, 별표 두 개짜리 구독 패턴과 인터셉터가 없던 `/topic/notification/{id}` 가 그대로 열려 있었다. **새 모듈이 인가를 빠뜨리면 열리는 게 아니라 닫혀야 한다.**

- **CONNECT** — `Authorization: Bearer` 헤더에서 토큰을 꺼내 `TokenManager.isRevoked` 와 토큰 타입(`ACCESS`)을 확인하고, `accessor.user` 에 회원 id 를 심는다. 채널 전체에 한 번만 걸려 있다(`ChatWebSocketConfiguration`). **소켓은 한 번 열리면 계속 살아 있어서 연결 시점에 못 막으면 그 뒤로 검사할 기회가 없다.**
- **SUBSCRIBE** — `common` 의 `WebSocketSubscriptionGate` 가 모든 구독을 받아, 등록된 `core.SubscriptionAuthorizer` 중 `supports` 가 참인 것에게 묻고 **하나도 없으면 거부한다.** 새 실시간 토픽을 만들면 모듈 `infrastructure` 에 `{Domain}SubscriptionAuthorizer` 를 `@Component` 로 추가하는 것이 전부다. 인터셉터를 새로 달지 않는다 — 다는 순간 기본 통과가 다시 생긴다.
  - 목적지는 **끝을 고정한 정규식**(`Regex("^/topic/chat/room/(\\d+)$")`)으로만 통과시킨다. 심플 브로커는 구독 목적지에 별표 와일드카드를 허용하므로, 방 번호 자리를 느슨하게 열면 전체 방을 한 번에 빨아간다. 숫자만 허용한다.
  - 게이트가 인증(`accessor.user`)까지 확인하므로 authorizer 는 순수 판정만 한다. 프레임워크 타입을 모른다.
- **모듈 인터셉터의 부수 효과는 게이트 뒤에 등록한다.** `registration.interceptors(gate, ...)` 순서를 지킨다. 앞에 두면 인가에 실패한 구독이 "보는 중"으로 기록된다.
- **UNSUBSCRIBE** — 프레임에 목적지가 없고 구독 id 만 온다. SUBSCRIBE 때 `구독 id → 목적지` 를 세션 속성에 남겨두고 여기서 꺼내 정리한다.
- **세션 종료** — 앱이 강제 종료되면 UNSUBSCRIBE 없이 소켓만 끊긴다. 인바운드 인터셉터는 그걸 못 보므로 `ApplicationListener<SessionDisconnectEvent>` 에서 정리한다. 안 하면 그 회원이 영원히 "보는 중"으로 남아 알림이 통째로 사라진다. 알림 판정은 `viewers()` 를 `checkOnline()` 과 교집합해 한 겹 더 막는다.

브로커는 인메모리(`enableSimpleBroker`)라 자기 JVM 에 붙은 세션에만 전달한다. 인스턴스가 여러 대면 다른 서버에 붙은 상대가 못 받는다. `RedisMessageBroadcaster` 가 pub/sub 으로 그 간극을 메우니, **서비스 코드는 `SimpMessagingTemplate` 이 아니라 `core.MessageBroadcaster` 포트를 쓴다.**

---

## 9. 비동기 / 스케줄링

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
- 튜닝 상수(`chunk`, `tries`, `threads`)는 부모의 `open val` 을 override 해 조정한다. **베이스 클래스 생성자에서 `open val` 을 읽지 않는다** — 하위 클래스 값이 아직 0 이라 `Semaphore(0)` 으로 전체가 선다. `by lazy` 로 미룬다.

### 분산 락 (`@DistributedLock`)

`infra/redis` 의 Redisson 기반 AOP 어노테이션. 옵션은 `prefix`, `keys`(SpEL 배열), `leaseSecs`(0 이하면 자동 갱신), `waitMs`·`retries`(획득 재시도), `transactional`(락 획득 후 트랜잭션 시작), `throwOnFailure`(기본 `false` — 실패 시 조용히 스킵). 락 키는 파라미터에 `@LockKey` 를 붙이거나 `keys` 에 SpEL 을 준다.

```kotlin
@DistributedLock(prefix = "lock:profile-image:", retries = 20, waitMs = 100, transactional = true, throwOnFailure = true)
fun confirmAdditionalImage(@LockKey memberId: Long, fileUrl: String): ProfileImage

@DistributedLock(prefix = "lock:wave-join:", keys = ["#roomId"])
fun join(roomId: Long, memberId: Long)
```

- **스케줄러 전용이 아니다.** 여러 인스턴스에서 동시 실행되면 안 되는 쓰기(개수 제한 검사 후 삽입 등)에도 `transactional = true` 로 건다.
- **self-invocation 에 주의한다.** Spring AOP 는 프록시 기반이라 같은 클래스 안에서 `this.method()` 로 부르면 advice 가 아예 안 탄다. 락이 걸려야 하는 메서드는 별도 `@Component` 빈으로 분리한다. (`ProfileImageLocker` 가 `ProfileService` 와 분리된 이유)
- **프록시가 씌워진 클래스의 메서드는 `open` 이어야 한다.** `@DistributedLock` 이 붙으면 CGLIB 프록시가 만들어지는데, **프록시 인스턴스는 필드가 비어 있고 `final` 메서드는 오버라이드되지 않아 위임 없이 프록시에서 그대로 실행된다.** `@Autowired lateinit` 필드를 쓰는 순간 `has not been initialized` 로 터진다. self-invocation 과 뿌리는 같지만 **이건 외부에서 호출해도** 터진다. (`OutBoxHistoryCleaner.cleanBefore` 가 `final` 이라 실제로 겪었다.) Kotlin 은 기본이 `final` 이라 놓치기 쉽다 — `kotlin-spring` 플러그인이 `@Component` 등이 붙은 **클래스**는 자동으로 열어주지만 **베이스 클래스의 일반 메서드는 안 열어준다.**
- 스케줄러 중복 실행 방지처럼 **놓쳐도 다음 주기에 만회되는 경우**는 `throwOnFailure = false`.
- **체크+저장 원자화를 Lua(EVAL)로 직접 짜기 전에 `@DistributedLock` 을 먼저 고려한다.** Redisson 기본 코덱은 바이너리 직렬화라 Lua 인자가 원시 바이트로 넘어가고 `tonumber()`/`SISMEMBER` 비교가 조용히 깨진다. Lua 가 꼭 필요하면 `getScript(StringCodec.INSTANCE)` 로 코덱을 명시한다 — `DailyRateLimiter` 가 그 방식이다.

### Redis 직접 사용

- **Redisson 코덱은 final 타입에 `@class` 를 안 붙인다.** 붙지 않으면 캐시 read 가 100% 실패해 무한 플래핑이 된다.
- **`Long` 을 Redis 셋에 넣지 않는다.** JSON 코덱으로 `Integer` 가 돌아와 `contains(1L)` 이 조용히 false 다.

### Kafka 컨슈머

`api/{Domain}Consumer.kt` 에 둔다. 외부 메시지 계약과 내부 모델이 다르면 **컨슈머에서 변환하고 왜 변환하는지 주석을 남긴다.**

```kotlin
@KafkaListener(topics = ["chat-message-sent"], groupId = "notification")
fun onChatMessageSent(event: ChatMessageSentEvent) { ... }
```

---

## 10. 스키마 마이그레이션 (Flyway)

- 파일 위치: `infra/rdb/src/main/resources/migration/V{n}__*.sql`
- 운영·개발·테스트 **모두 `ddl-auto: validate`**. 통합테스트도 Flyway 를 타므로 마이그레이션 자체가 검증된다.
- **이미 적용된 V 파일은 절대 수정하지 않는다.** 체크섬 불일치로 기동이 실패한다. 고칠 게 있으면 새 V 파일을 만든다.
- **인덱스 생성 방식은 테이블에 행이 있는지로 갈린다.**
  - 일반 `create index` 는 `SHARE` 락을 잡아 빌드가 끝날 때까지 그 테이블의 쓰기를 전부 세운다. 지금까지의 V 파일은 전부 같은 마이그레이션에서 방금 만든 빈 테이블에 걸어 락이 0초였다.
  - **그렇다고 `concurrently` 를 기본으로 삼지 마라.** `CREATE INDEX CONCURRENTLY` 는 **기존의 모든 트랜잭션이 끝나기를 기다린다.** 커넥션 풀이 스냅샷을 잡고 있으면 **영영 끝나지 않는다.** 실제로 `V8` 을 `concurrently` 로 썼을 때 통합테스트가 `Migrating ... [non-transactional]` 에서 **3시간 반 멈췄고**, 일반 `create index` 로 바꾸니 전체 빌드가 **2분 52초**에 끝났다. 운영에서도 장수 트랜잭션이 하나 있으면 배포가 그대로 선다.
  - **판단 기준**: 운영에 행이 없으면(이 저장소는 아직 배포 전이다) 일반 `create index`. 행이 쌓인 뒤라면 먼저 데이터를 줄이는 마이그레이션을 따로 배포하고, 그다음 `concurrently` 를 별도 V 파일로 분리해 열린 트랜잭션이 없는 시점에 돌린다. `concurrently` 는 트랜잭션 안에서 못 돌므로 `-- flyway executeInTransaction=false` 가 필요하고, 실패하면 `INVALID` 인덱스가 남아 수동 정리를 부른다.
  - 어느 쪽을 택하든 **그 이유를 SQL 주석에 남긴다.**
- **`@Column(nullable = false)` 를 새로 붙이면 기존 행 백필 마이그레이션이 반드시 따라와야 한다.** 안 하면 NULL 을 읽어 Kotlin non-null 프로퍼티에서 NPE 가 난다.
- 정렬·커서는 `created_at` 이 아니라 **id 시퀀스**나 도메인 시퀀스 기준. 인스턴스 간 시계 차이로 순서가 뒤집힌다.

---

## 11. i18n

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

## 12. 테스트

**모든 스펙은 `BehaviorSpec` 하나로 쓴다.** 계층에 따라 `DescribeSpec` 으로 갈아타지 않는다 — 이 저장소에 `DescribeSpec` 은 한 개도 없다. 무게중심은 단위 테스트(컨텍스트 없음 + MockK)에 두고, `@SpringBootTest` 는 부수 효과(DB·Outbox·롤백)를 확인해야 할 때만 만든다. E2E 는 `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestRestTemplate`.

### 단위 — Kotest `BehaviorSpec` + MockK

```kotlin
class MemberServiceTest : BehaviorSpec({

    val repo = mockk<MemberRepository>()
    val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
    val service = MemberService(repo, publisher)

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
- 검증하지 않을 협력자는 `mockk(relaxed = true)`. 단언은 kotest 매처(`shouldBe`, `shouldThrow`, `shouldHaveSize`). JUnit `assertEquals` 를 섞지 않는다.
- **"하지 않음"도 검증한다.** 의도적 설계는 테스트로 고정해야 나중에 안 깨진다.
  ```kotlin
  Then("핸들만 바뀌고 온라인 트래커는 건드리지 않는다 (id 로 keying 하므로)") {
      verify(exactly = 0) { tracker.toOnline(any()) }
  }
  ```
- **어노테이션 누락은 리플렉션으로 고정한다.** `@Scheduled`·`@DistributedLock` 이 빠져도 컴파일과 테스트는 통과한다. 아웃박스 스케줄러 테스트들이 선례다.

### 통합 — Testcontainers

- DB 는 **Testcontainers PostgreSQL**. H2 로 대체하지 않는다.
- 외부 인프라는 `@TestConfiguration` + `@Primary` + `mockk(relaxed = true)` 로 대체.
- `SpringBootTest` 를 쓸 땐 `override fun extensions() = listOf(SpringExtension)`, 본문은 `init { }` 블록.
- **부수 효과까지 검증한다.** 유스케이스 하나에 대해 "DB 반영 + Outbox 기록 + 실패 시 롤백"을 각각 `Then` 으로 나눠 확인한다.
- 모듈에 첫 `@SpringBootTest` 를 넣을 때는 테스트 전용 진입점 `Test{Domain}Application.kt` 를 함께 만든다.
- **동시성 결함은 실제 동시성으로 검증한다.** mockk 로는 못 잡는다. `ReportConcurrencyIntegrationTest`, `MemberStatusCacheRaceTest`, `MemberHandleConcurrencyIntegrationTest` 가 선례다. **동시성 테스트는 일반 CRUD 테스트와 같은 클래스에 섞지 않는다** — 남긴 행이 페이지네이션·카운트 검증을 오염시킨다.
- 여러 모듈을 가로지르는 E2E 는 `app/api/src/test/` 에 둔다. 모듈 하나짜리 컨텍스트로는 조립이 안 된다.

### 안티패턴

- **`Thread.sleep()` 금지.** 비동기 결과는 kotest `eventually(3.seconds) { ... }` 로 기다린다.
- private 메서드를 직접 테스트하지 않는다. 테스트 간 상태를 공유하지 않는다.
- `any()` 남발 금지. 의미가 걸린 인자는 `eq()` 로 구체값을 맞춘다.
- 한 `Then` 에 관련 없는 단언을 몰아넣지 않는다.
- **버그를 "예상 동작"으로 인코딩하지 않는다.** 500 이 나는 게 실은 버그인데 기대값을 500 으로 적어 통과시키는 식. 올바른 기대값을 적고 구현을 고친다.

### 수정은 테스트로 증명한다 — 예외 없다

**"빌드 통과"는 아무것도 증명하지 않는다.** 고치기 전에도 빌드는 통과했다.

1. **수정 전 코드에서 새 테스트가 빨간불인 것을 먼저 확인하고, 그 실패 메시지를 보고에 적는다.** 빨간불이 안 되면 그 테스트는 결함을 안 덮는 것이다. relaxed mock 이 non-null 을 돌려주는 바람에 `OncePerRequestFilter` 가 아예 안 타고도 초록이 뜬 적이 있다.
2. **테스트가 프로덕션 경로를 타는지 본다.** 리터럴 SQL, 손으로 짠 대역, 내부 메서드 직접 호출은 실제 경로를 안 덮을 수 있다. `#45` 의 인덱스 테스트는 리터럴 SQL 로 EXPLAIN 해서 프로덕션 경로(바인드 파라미터)를 안 덮은 채 초록불이었다 — 그대로 머지했으면 아무것도 안 고친 PR 이 됐다.
3. **스키마·설정 변경은 런타임으로 확인한다.** 마이그레이션 파일을 만든 것으로는 부족하다. `bootRun` 으로 적용하고 `\d`·`EXPLAIN`·실제 빈 속성을 눈으로 본다. 측정용 데이터를 넣었으면 **반드시 원복한다.**

---

## 13. 새 모듈 체크리스트

- [ ] `build.gradle.kts` 를 만든다 (아래 템플릿). `settings.gradle.kts` 가 자동 등록한다
- [ ] **`app/api/build.gradle.kts` 에 `implementation(project(":module:<name>"))` 를 추가한다.** 빠뜨리면 앱에 아예 안 실린다
- [ ] 4계층 패키지(`api`/`application`/`domain`/`infrastructure`)를 먼저 만든다
- [ ] 다른 모듈이 써야 하는 인터페이스/이벤트는 계약 모듈 `module/<name>-api` 에 (`build.gradle.kts` 에 `kotlin.jvm` 플러그인만, 의존성 0). 인프라가 소유하는 계약이거나 소유자가 애매하면 `core`
- [ ] 소비하는 쪽 `build.gradle.kts` 에 **실제로 쓰는 `*-api` 만** 추가한다. 계약 모듈은 `app/api` 에 등록하지 않는다
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
    implementation(project(":core"))          // 캐시·브로드캐스터 등 인프라 계약을 쓸 때만
    implementation(project(":module:<name>-api"))   // 자기 계약 모듈
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

---

## 14. 반복해서 터진 함정

실제로 이 저장소에서 발생했던 것들. 전부 "조용히 잘못되는" 종류라 테스트 없이는 못 찾는다. 각 항목의 상세는 위 해당 절에 있다.

| 함정 | 증상 |
|---|---|
| 인증만 하고 인가 안 함 | 로그인한 아무나 남의 대화 구독. 와일드카드 토픽으로 전체 흡입 |
| fail-open 검증 (`x != null && x != y`) | 헤더를 빼기만 하면 검증 통째로 우회 |
| 클라이언트가 준 URL 저장 | 외부 주소 삽입, presigned 서명 노출 |
| 엔티티 읽고-쓰기로 카운터 증가 | 동시 요청 시 증가 유실 |
| `open val` 을 베이스 생성자에서 읽기 | 하위 클래스 값이 아직 0. `Semaphore(0)` 으로 전체 정지 |
| Redisson 코덱이 final 타입에 `@class` 미부여 | 캐시 read 100% 실패 → 무한 플래핑 |
| `Long` 을 Redis 셋에 저장 | `Integer` 가 돌아와 `contains(1L)` 이 조용히 false |
| `@Modifying` 쿼리에 트랜잭션 없음 | `No EntityManager with actual transaction` |
| i18n 키 누락 | 키 문자열이 그대로 사용자 응답에 노출 |
| 로케일 접미사 없는 `messages.properties` 부재 | `messageSource` 빈이 아예 안 생겨 **전 응답**이 키 문자열 |
| WebSocket 구독 인가를 "내 접두사 아니면 통과"로 | 아무도 안 맡는 목적지가 열린 채 남음 |
| 주석 안의 `/*` | Kotlin 중첩 주석이 열려 뒤 코드가 통째로 주석 처리 |
| kotest `afterEach { clearMocks }` 로 스텁까지 삭제 | `Then` 블록 사이에 돌아 `verify` 가 빈 기록을 본다 |
| 크래시한 클라이언트가 "보는 중"으로 남음 | 알림이 영영 안 감 |
| `@Retryable` 을 `@EnableRetry` 없이 사용 | 조용히 안 돈다 |
| 프록시 대상 클래스의 `final` 메서드 | CGLIB 이 오버라이드를 못 해 `lateinit ... has not been initialized` |
| `create index concurrently` 를 열린 트랜잭션과 함께 | 무한 대기. 통합테스트가 3시간 반 멈춘 적이 있다 |
| `StringPath.search()` 에 `f_unaccent()` GIN 인덱스 누락 | 쿼리는 통과하지만 매번 Seq Scan |

---

## 15. 서브에이전트

모델을 골라 넘긴다. **구현은 sonnet 이 기본**이고, 판단이 필요 없는 기계적 작업만 haiku 로 내린다.

| 모델 | 맡기는 일 |
|---|---|
| **haiku** | i18n 12개 번들에 같은 키 채워넣기, 파일 위치·심볼 찾기, 단순 문자열 치환, 목록 수집. **설계 판단을 맡기지 않는다** |
| **sonnet** | **구현·수정 전부.** DTO·컨트롤러 매핑, Flyway V 파일, 모듈 신설, 동시성·정합성 수정, 모듈 경계 작업 |
| **opus** | 이 문서의 갱신, 그리고 사용자가 명시적으로 요청할 때 |

예전에는 "조용히 잘못될 수 있는 것"을 opus 로 올렸는데, 그 방어선은 모델 등급이 아니라 **리뷰와 테스트**가 맡는다. 이 저장소에서 조용히 잘못된 것들은 전부 리뷰가 잡았다. 대신 **지시서를 촘촘히 쓴다** — sonnet 이 흘리기 쉬운 것(`@field:` 타깃 누락, 엔티티를 `data class` 로 선언, 조건을 메서드명으로 이은 파생 쿼리, `@Scheduled` 에 `@DistributedLock` 누락, `!!` 사용)과 §12 의 "수정은 테스트로 증명한다"를 매번 명시한다.

**모든 지시서에 `./local-infra-start.sh` 실행 금지를 명시한다.** 도커 볼륨이 `docker/volume/` 바인드 마운트라 워크트리에서 띄우면 마운트가 그 워크트리로 잡히고, 워크트리를 지우면 stale 이 돼 Redis 가 `stop-writes-on-bgsave-error` 로 **모든 쓰기를 거부한다.** 인프라는 저장소 루트에서 한 번만 띄우고 워커에게는 "`localhost` 로 붙어라"만 쓴다. Testcontainers 는 자기 컨테이너를 따로 띄우므로 무관하다.

### 기능 작업 워크플로 (orchestration)

**구현자와 리뷰어를 분리한다.** 구현한 모델이 자기 코드를 리뷰하면 같은 맹점을 그대로 지나간다. 구현(sonnet, 워크트리) → 리뷰(agy, 읽기 전용, PR 코멘트) → 수정 루프 → 코디네이터가 머지.

#### 1) 구현 — sonnet, 워크트리 기반

```bash
orca orchestration task-create --spec "<지시서>" --json
orca orchestration worker-start --task <task_id> \
  --worktree new-top-level --name <작업명> \
  --agent claude --model sonnet --setup run --json
```

**반드시 새 워크트리에 띄운다.** 같은 워크트리에서 여러 에이전트가 돌면 브랜치가 섞인다 — 코디네이터가 브랜치를 바꿨다가 구현 에이전트의 커밋이 그 브랜치에 얹혀 origin 까지 올라간 적이 있다. 지시서에 **푸시와 PR 생성까지** 시킨다.

#### 2) 리뷰 — agy, 읽기 전용

`agy` 는 orca 의 known-agent 가 아니라 **프롬프트 자동 주입이 안 된다.** `worker-start --agent`, `--terminal`, `dispatch --inject` 셋 다 `agent_prompt_stalled` 로 실패한다. `--inject` 없는 `dispatch` 로 추적만 붙이고 프롬프트를 손으로 넣으면 `worker_done` 까지 정상으로 돈다.

```bash
# 리뷰마다 새 터미널을 만든다 — 재사용하면 스크롤백의 옛 dispatch id 를 집어 capability is revoked 로 거부된다
orca terminal create --worktree <worktree-id> --title "review-prN (agy)" --command "agy" --json

# tui-idle 만으로는 부족하다. 로그인이 끝날 때까지 기다린다
until orca terminal read --terminal <handle> --json | grep -q "Google AI Pro"; do sleep 3; done

orca orchestration task-create --spec "..." --json
orca orchestration dispatch --task <task_id> --to <handle> --json   # --inject 없이
orca terminal send --terminal <handle> --enter --json --text "<지시서 경로>를 읽고 그대로 수행해라. 끝나면 orca orchestration send --type worker_done ... --outcome succeeded --json 를 실행해라"
orca orchestration check --wait --types worker_done,question --timeout-ms 900000 --json
```

**새 워크트리에서 agy 를 처음 띄우면 "Do you trust the contents of this project?" 에서 멈춘다.** 답하기 전엔 `Google AI Pro` 가 안 떠서 위 루프가 타임아웃한다. 기본 선택이 `Yes` 라 빈 텍스트로 엔터만 보내면 통과한다. 대기 루프가 이유 없이 타임아웃하면 먼저 `terminal read` 로 이걸 확인해라.

지시서는 **`superpowers:requesting-code-review`** 의 `code-reviewer.md` 템플릿을 따르고, **지시서 안에서 agy 에게도 그 스킬을 직접 읽으라고 시킨다** — agy 는 Claude Code 가 아니라 스킬이 자동으로 안 붙어서, 코디네이터가 템플릿대로 쓰는 것만으로는 리뷰어가 절차·심각도 기준을 갖지 못한다. 맨 앞에 `code-reviewer.md` 의 **절대 경로**를 주고 그 아래 플레이스홀더(무엇을 만들었나 / 요구사항 / `BASE_SHA`..`HEAD_SHA`)를 채운다. 핵심만 옮기면:

- **세션 히스토리를 주지 않는다.** 요구사항과 `BASE_SHA`..`HEAD_SHA` 범위만 정밀하게
- **read-only 명시.** 워킹트리·인덱스·HEAD·브랜치 금지. 다른 리비전이 필요하면 `git worktree add /tmp/review-<sha>`
- **리뷰어가 다시 서브에이전트를 띄우지 못하게 막는다.** 디프가 크면 스스로 나눠 보게
- **심각도 분리** (Critical 즉시 / Important 머지 전 / Minor 기록만), 판정은 `승인` / `조건부 승인(N건 수정 후)` / `반려` 중 하나로 강제
- 지적마다 **"어떤 입력·상황에서 실제로 터지나"** 를 요구. 없으면 추측이다

**agy 는 코드를 고치지 않는다.** 리뷰 결과는 PR 코멘트로 남긴다 (`gh pr comment <N> --body-file .omo/review-prN.md`). **지시서는 파일로 두고 경로만 보낸다** — `terminal send` 로 긴 본문을 밀면 TUI 가 깨진다. `--model`/`--effort` 는 agy 자체 플래그라 `--command "agy --model ... --effort high"` 로 넘긴다.

#### 3) 수정 루프

Critical·Important 가 있으면 **구현했던 그 sonnet 에이전트에게** 고치게 한다. 새 에이전트를 띄우지 않는다 — 그 에이전트가 코드 맥락을 이미 갖고 있다. **코디네이터는 코드를 직접 고치지 않는다.** 아무리 작아 보여도 넘긴다 — 코디네이터가 손대면 그 변경만 리뷰를 안 거친 채 머지되고 컨텍스트도 쏠린다. 문서 수정은 코디네이터가 해도 된다.

**정착된 dispatch 에는 `orchestration send` 가 안 먹는다.** 에이전트는 `worker_done` 을 보낸 뒤 인박스를 폴링하지 않는다. **새 태스크를 만들어 같은 터미널에 다시 붙인다.**

```bash
H=$(orca orchestration worker-show --dispatch <settled_dispatch> --json | grep -o '"agent_terminal_handle": "[^"]*"' | cut -d'"' -f4)
orca orchestration task-create --spec "<리뷰 반영 지시>" --json
orca orchestration worker-start --task <new_task_id> --worktree id:<worktree> --terminal $H --json
```

`--worktree` 를 함께 주지 않으면 `terminal_worktree_mismatch` 로 실패한다. **sonnet 이 사용량 한도에 걸려 못 이어갈 때만** 코디네이터가 이어받고, 그때도 이유를 사용자에게 먼저 말한다.

#### 4) 머지와 정리

승인이 나면 **코디네이터가 직접** 머지한다.

```bash
gh pr merge <N> --squash --delete-branch
orca orchestration worker-release --dispatch <sonnet_dispatch_id> --json
orca terminal close --terminal <agy_handle> --json
```

**받은 `worker_done` 은 그 자리에서 ack 한다.** 안 하면 그 delivery 가 계속 재생돼 다음 대기를 가로챈다. 실제로 두 번 겪었다.

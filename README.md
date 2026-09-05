# Langlez Backend

언어교환 모바일 앱(iOS/Android)의 서버. Kotlin / Spring Boot 멀티모듈 모듈러 모놀리스.

- **언어·런타임**: Kotlin 2.2.21 / Java 21 (Virtual Threads 사용, 코루틴 미사용)
- **프레임워크**: Spring Boot 3.5.8 (Web, Security, Data JPA + QueryDSL 7.1, Data MongoDB, WebSocket/STOMP), springdoc-openapi 2.8.9
- **저장소**: PostgreSQL · MongoDB · Redis(Redisson)
- **메시징**: Kafka (트랜잭셔널 아웃박스 경유)
- **스키마**: Flyway (`ddl-auto: validate`)

관련 문서:

- `CLAUDE.md` — **코드 규약**. `module/member` 를 기준 모듈로 삼아 실제 코드에서 역으로 추출했다. 새 코드를 쓰기 전에 반드시 읽는다. 반복해서 터졌던 함정 목록도 여기(§5) 있다
- `module/CLAUDE.md` — **도메인 모듈 규약**. 4계층(`api`/`application`/`domain`/`infrastructure`) 구현 규약 — 엔티티·저장소·서비스·API·비동기·마이그레이션·i18n·테스트·새 모듈 체크리스트. `module/` 아래를 건드리기 전에 읽는다
- `ARCHITECTURE.md` — **이벤트 아키텍처**. 토픽 발행·수신과 모듈별 처리, 아웃박스·멱등성·실시간 전달 경로. 이벤트를 새로 추가하거나 컨슈머를 건드리기 전에 읽는다
- `docs/superpowers/plans/` — 단위 작업별 상세 계획서(이력)

---

## 1. 빠른 시작

```bash
./local-infra-start.sh          # PostgreSQL / Redis / Kafka / 모니터링 컨테이너 기동
./gradlew build                 # 전체 빌드 + 테스트 (Testcontainers 사용, Docker 필요)
./gradlew :app:api:bootRun      # 서버 실행 (http://localhost:8080)
./gradlew :module:member:test   # 모듈 단위 테스트
```

`local-infra-start.sh` 는 `docker/postgresql.yml`, `docker/redis.yml`, `docker/kafka.yml`, `docker/mongodb.yml`, `docker/monitoring.yml` 을 `-p langlez` 단일 compose 프로젝트로 합쳐 올린다. **MongoDB 도 포함된다.** 없어도 앱은 뜨지만(인덱스 생성은 기동 경로 밖으로 빠져 있다) `chat` 이 동작하지 않고 health 가 DOWN 이라, 빼두면 확인할 때마다 손으로 덧붙이게 된다.

### 인프라가 이상할 때

컨테이너 볼륨은 전부 `docker/volume/` 아래 **바인드 마운트**다. 여기서 두 가지가 실제로 터졌다.

- **`./local-infra-start.sh` 를 git 워크트리에서 실행하지 않는다.** 마운트 경로가 그 워크트리로 잡히고, 워크트리를 지우면 마운트가 stale 이 된다. Redis 는 RDB 저장에 실패하고 `stop-writes-on-bgsave-error` 때문에 **모든 쓰기를 거부한다**(`MISCONF ...`). 항상 저장소 루트에서 띄운다.
- **Kafka 가 `Invalid cluster.id in: /var/lib/kafka/data/meta.properties` 로 죽으면** `docker/kafka.yml` 의 `CLUSTER_ID` 와 볼륨에 남은 값이 다른 것이다. `docker/volume/kafka/` 를 통째로 비우고 다시 띄운다. 로컬 개발 데이터라 잃을 게 없다.

로컬 접속 정보(`app/api/src/main/resources/application.yml` 기준):

| 대상 | 주소 |
|---|---|
| PostgreSQL | `jdbc:postgresql://localhost:5432/langlez_db` (admin/admin) |
| MongoDB | `mongodb://localhost:27017/langlez_db` |
| Redis | `localhost:6379` |
| Kafka | `localhost:9092,9094,9096` (3노드) |
| WebSocket | `/ws/chat`, `/ws/wave` (STOMP, 브로커 prefix `/topic`, 앱 prefix `/app`) |

운영 설정은 `application-production.yml` 이 환경 변수로 덮어쓴다.

### 시작 전에 알아야 할 것

- **모바일 전용이다.** 쿠키를 쓰지 않는다. 토큰은 헤더로만 오간다.
- **1인 1기기 정책.** `X-Device-Id` 헤더가 필수고, 다른 기기로 로그인하면 기존 세션이 끊긴다.
- **`ddl-auto: validate`.** 스키마 변경은 Flyway 로만 한다. 엔티티와 마이그레이션이 어긋나면 기동 시점에 죽는다.
- **`open-in-view: false`.** 트랜잭션 밖에서 LAZY 연관을 만지면 터진다.
- **탈퇴 회원 데이터는 지우지 않는다.** 익명화도 안 한다. 재가입 후 같은 문제를 반복하는 회원 추적이 목적인 의도된 정책이다.

---

## 2. 아키텍처

### 모듈 구조

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

`settings.gradle.kts` 가 `infra/`, `module/` 하위를 자동 스캔한다. `build.gradle.kts` 만 만들면 서브프로젝트로 등록된다.
다만 **등록만으로는 앱이 그 모듈을 로드하지 않는다** — `app/api/build.gradle.kts` 에 `implementation(project(":module:<name>"))` 을 직접 추가해야 한다.
**계약 모듈(`module/*-api`)만 예외다** — 빈이 없어 소비 모듈이 물면 런타임 클래스패스에 그대로 올라온다.

현재 도메인 모듈: `member`, `auth`, `attachment`, `profile`, `lang`, `matching`, `chat`, `notification`, `follow`, `block`, `moderation`, `echo`, `wave`.
현재 계약 모듈: `member-api`, `follow-api`, `block-api`, `lang-api`, `matching-api`, `moderation-api`, `attachment-api`, `notification-api`, `chat-api`, `echo-api`.

### 4계층

모든 도메인 모듈은 아래 4계층을 갖는다. 계층 이름과 depth 를 임의로 바꾸지 않는다.

```
module/member/src/main/kotlin/com/langlez/member/
├── api/                # 외부 진입점 (HTTP, Kafka, 애플리케이션 이벤트) + 요청·응답 DTO
├── application/        # 유스케이스 조합, 트랜잭션 경계
├── domain/             # 엔티티 + 저장소 포트(인터페이스)
└── infrastructure/     # 포트의 구현(어댑터), jpa/, outbox/
```

**`matching` 만 예외로 `api`/`application` 두 계층뿐이다.** 자기 테이블이 없는 조합 모듈이라
`domain` 에 넣을 엔티티도 `infrastructure` 가 구현할 포트도 없다. 새 모듈에 복사하지 마라.

의존 방향은 한쪽으로만 흐른다.

```
api ──▶ application ──▶ domain ◀── infrastructure
```

- `domain` 은 다른 계층을 import 하지 않는다. 프레임워크 의존은 영속성/감사 애노테이션까지만.
- `application` 은 `domain` 의 포트 인터페이스만 안다. `infrastructure` 구현 클래스를 직접 참조하지 않는다.
- `infrastructure` 가 `domain` 인터페이스를 구현하며 방향을 뒤집는다.
- 모듈 간에는 서로를 직접 참조하지 않는다. 상대 모듈의 `{도메인}-api` 계약(포트·이벤트)만 본다.

### 통신 규칙

| 목적 | 수단 |
|---|---|
| 모듈 간 상태 변경 전파, 유실되면 안 되는 것 | **Kafka** (아웃박스 경유). 이벤트 DTO 는 발행 모듈의 `{도메인}-api` 에 |
| 응답을 기다려야 하는 조회 | **소유 모듈의 `{도메인}-api` 포트** |
| 접속 중인 사용자에게 실시간 전달 | **`core.MessageBroadcaster`** → Redis pub/sub → WebSocket |
| 고빈도 하트비트 | **Redis 직결** (Kafka 금지) |

**계약을 어디에 두는지의 기준은 소유자다.** 도메인이 구현하고 그 데이터를 소유하면 `module/{도메인}-api`,
`infra/*` 가 구현하거나 소유자가 하나로 정해지지 않으면 `core`.

| 계약 | 있는 곳 (패키지) | 역할 | 구현 |
|---|---|---|---|
| `MemberReader` | `member-api` (`com.langlez.member.contract`) | 계정 정보 + 상태 조회 | `member`(`MemberReaderImpl`) |
| `PushTokenReader` | 〃 | FCM 토큰 조회 | `member`(`MemberReaderImpl`) |
| `OnlineTracker` | 〃 | 접속·화면(viewing) 상태 | `member`(`MemberOnlineTracker`) |
| `MemberWriter` | 〃 | 정지·정지 해제 운영 조치 | `member`(`MemberSuspender`) |
| `Member{Created,HandleChanged,Withdrawn}Event` | 〃 | 회원 도메인 이벤트 | — |
| `FollowReader` | `follow-api` (`com.langlez.follow.contract`) | 팔로잉 id 목록·카운트 | `follow`(`FollowReaderImpl`) |
| `MemberFollowedEvent` | 〃 | 팔로우 이벤트 | — |
| `BlockReader` | `block-api` (`com.langlez.block.contract`) | 차단 관계 확인 | `block`(`BlockReaderImpl`) |
| `LanguageReader` | `lang-api` (`com.langlez.lang.contract`) | 언어 프로필 조회 + 상호보완 후보 질의 | `lang`(`LanguageReaderImpl`) |
| `MatchingReader` | `matching-api` (`com.langlez.matching.contract`) | 추천 회원 id. **아직 소비자 없음** | `matching`(`MatchingService`) |
| `MemberBlockedEvent` | 〃 | 차단 이벤트 (follow 가 컨슘해 팔로우를 끊는다) | — |
| `ReportWriter` | `moderation-api` (`com.langlez.moderation.contract`) | 신고 접수. **아직 소비자 없음** | `moderation`(`ReportService`) |
| `Storage` | `attachment-api` | presign / key 확정 | `attachment` |
| `Notificator` | `notification-api` | 알림 발송 | `notification` |
| `Chat{MessageSent,UserReported}Event` | `chat-api` | 채팅 도메인 이벤트 | — |
| `Echo{PostLiked,CommentCreated}Event` | `echo-api` | 피드 도메인 이벤트 | — |
| `CacheProvider` / `Cache` | `core` | 캐시 획득·조회·무효화 | `infra/redis`(`ResilientCacheProvider`) |
| `MessageBroadcaster` | 〃 | 토픽 팬아웃 | `infra/redis`(`RedisMessageBroadcaster`) |
| `MessageDeduplicator` | 〃 | 카프카 중복 처리 방지 | `infra/redis`(`RedisMessageDeduplicator`) |
| `SubscriptionAuthorizer` | 〃 | STOMP 구독 목적지 인가 판정 | `chat`·`wave`·`notification` 각 모듈 |

`SubscriptionAuthorizer` 가 `core` 에 남은 이유: 방향이 뒤집혀 있다. `common` 이 소비하고 도메인들이 구현하니 어느 `{도메인}-api` 에도 맞지 않는다.

**`common` 이 `module/member-api` 를 의존한다.** `JwtAuthenticationFilter` 가 매 요청 계정 상태를 본다.
원래도 있던 의존이 `core` 라는 이름 뒤에 가려져 있었을 뿐이고, 계약 모듈은 의존성이 0 이라 순환이 없다.

아웃박스가 필요한 이유: 저장과 이벤트 발행이 한 트랜잭션에 묶여야 "저장은 됐는데 이벤트는 유실"이 원천 차단된다. 단, **가장 빈번한 쓰기(채팅 메시지)는 별도 아웃박스 행 대신 문서의 `published` 플래그**로 처리해 쓰기 증폭을 없앴다.

### 저장소 분담

| 데이터 | 저장소 | 이유 |
|---|---|---|
| 회원·프로필·방·참여자·아웃박스 | PostgreSQL | 조인·트랜잭션 필요, 유한 증가 |
| 채팅 메시지 본문 + 첨부 | MongoDB | 무한 증가, 첨부 임베드로 조회 1회 |
| 접속·화면 상태·분산 락·캐시·wave 채팅 | Redis | 휘발성·고빈도 |

각 저장소의 연결·설정은 인프라 모듈이 소유한다 — `infra/rdb`(JPA·QueryDSL·Flyway), `infra/mongo`(리포지토리 스캔·인덱스 초기화), `infra/redis`(Redisson·캐시·분산 락). **도메인 모듈은 저장소 라이브러리를 직접 물지 않고 이 모듈들을 통해 받는다.**

### 스키마 관리

Flyway. `infra/rdb/src/main/resources/migration/V{n}__*.sql`. 현재 `V1__init.sql` ~ `V14__member_languages.sql`.
운영·개발·테스트 모두 `ddl-auto: validate`. **이미 적용된 V 파일은 절대 수정하지 않는다** (체크섬 불일치로 기동 실패). 고칠 게 있으면 새 V 파일을 만든다.

---

## 3. 신규 개발자 학습 투어

아래 순서로 읽으면 전체 구조가 한 번에 잡힌다. 각 단계는 실제 파일 경로다.

**1단계 — 조립과 실행**
`settings.gradle.kts`, `build.gradle.kts`, `app/api/src/main/kotlin/com/langlez/MainApplication.kt`, `app/api/src/main/resources/application.yml`.
모듈 자동 스캔, Virtual Thread 활성화, JPA/Mongo 리포지토리 자동 설정을 왜 제외했는지를 확인한다.

**2단계 — 계약**
`module/*-api/src/main/kotlin/com/langlez/{domain}/contract/` 와 `core/src/main/kotlin/com/langlez/core/`.
모듈 간 결합이 전부 이 인터페이스들을 통과한다. 도메인이 소유하는 포트·이벤트는 `{도메인}-api`, 인프라가 소유하는 것만 `core` 다.

**3단계 — 공용 웹·보안**
`common/src/main/kotlin/com/langlez/`: `filter/JwtAuthenticationFilter.kt`, `config/WebSecurityConfiguration.kt`, `security/TokenManager.kt`, `annotation/MemberId.kt`(+`MemberIdResolver`), `GlobalRestControllerAdvice.kt`, `exception/LanglezException.kt`.
인증된 사용자 id 가 컨트롤러까지 어떻게 전달되는지, 예외가 어떻게 i18n 메시지로 바뀌는지 본다.

**4단계 — 기준 모듈 `member`**
`module/member/src/main/kotlin/com/langlez/member/`: `domain/Member.kt`(+`MemberAudit.kt`) → `domain/MemberRepository.kt` → `infrastructure/MemberRepositoryImpl.kt`(2단계 캐시) → `api/MemberAPI.kt` + `api/MemberController.kt`(Swagger 분리) → `api/MemberPingController.kt`(고빈도 핑을 Redis 직결로).
**여기 코드가 규약의 정답이다.** 다른 모듈이 이것과 다르면 다른 쪽이 틀린 것이다.

**5단계 — 인증 흐름**
`module/auth/src/main/kotlin/com/langlez/auth/`: `oauth2/OAuth2SuccessHandler.kt` → `application/AuthService.kt` → `application/AccessContext.kt` → `api/AuthController.kt`.
OAuth2(Google/Apple) 성공 이후 JWT 발급, `X-Device-Id` 기반 1인 1기기 세션 탈취 처리를 따라간다.

**6단계 — 아웃박스**
`infra/rdb/src/main/kotlin/com/langlez/rdb/outbox/`(`OutBox`, `OutBoxRepository`, `OutBoxProcessor`, `OutBoxHistoryProcessor`) → `module/member/api/MemberEventListener.kt`(`@TransactionalEventListener(BEFORE_COMMIT)`) → `module/member/infrastructure/outbox/MemberOutBoxScheduler.kt`(`@Scheduled` + `@DistributedLock`).

**7단계 — 실시간 채팅**
`module/chat/src/main/kotlin/com/langlez/chat/`: `ChatWebSocketConfiguration.kt`(모듈 루트, `/ws/chat`, CONNECT 인증 + 게이트 등록) → `application/ChatService.kt` → `application/ChatMessagePublisher.kt` → `infrastructure/mongo/ChatMessageRepositoryImpl.kt` → `application/ChatReconciler.kt`(Mongo↔Postgres 이중 쓰기 창 복구).

**8단계 — 이벤트 소비와 알림**
`module/notification/api/NotificationConsumer.kt`(`chat-message-sent`) → `application/NotificationService.kt`(3상태 판정) → `infrastructure/FcmPushSender.kt`.
`module/moderation/api/ReportConsumer.kt`(`chat-user-reported`)와 `module/follow/api/FollowConsumer.kt`(`member-blocked`)도 같이 본다.

**9단계 — Redis 인프라**
`infra/redis/src/main/kotlin/com/langlez/redis/`: `cache/ResilientCache.kt`·`cache/ResilientCacheProvider.kt`(Redis + Caffeine 폴백), `distributedLock/DistributedLock.kt`·`DistributedLockAspect.kt`, `broadcast/RedisMessageBroadcaster.kt`, `config/RedissonConfiguration.kt`.

**10단계 — 나머지 도메인**
`module/echo/application/EchoService.kt`(타임라인·좋아요·댓글·해시태그), `module/wave/application/WaveService.kt` + `WaveWebSocketConfiguration.kt`(모듈 루트, `/ws/wave` 등록만) + `infrastructure/WaveSubscriptionAuthorizer.kt`(참여자 판정).

### 복잡도 핫스팟

바꿀 때 특히 조심할 곳. 전부 실제로 사고가 났거나, 동시성·정합성이 걸려 있다.

| 파일 | 왜 위험한가 |
|---|---|
| `module/chat/application/ChatService.kt` | 방 생성·읽음·나가기·첨부가 한곳에. Mongo 쓰기와 Postgres 비정규화 카운터가 함께 움직인다 |
| `module/chat/application/ChatReconciler.kt` | 이중 쓰기 실패 복구. 활성 방 수만큼 Mongo 왕복이 붙는다 |
| `module/member/infrastructure/MemberRepositoryImpl.kt` | 2단계 캐시. 보조 캐시는 PK 만 담고, 변경 가능한 키(handle)는 읽을 때 재검증한다 |
| `infra/redis/cache/ResilientCache.kt`, `ResilientCacheProvider.kt` | Redis 장애 시 Caffeine 폴백. 직렬화 타입이 어긋나면 조용히 전부 miss 난다 |
| `common/filter/JwtAuthenticationFilter.kt` | 예외 catch 범위를 넓히면 Spring Security 예외 경로를 가로챈다. 회귀 테스트가 붙어 있다 |
| `infra/rdb/outbox/OutBoxProcessor.kt` | `open val` 튜닝 상수를 베이스 생성자에서 읽으면 전체가 멈춘다 |
| `module/echo/application/EchoService.kt` | 원자적 카운터 갱신, 차단·팔로우 필터가 타임라인 전 경로에 얽힌다 |
| `module/wave/application/WaveService.kt` | 정원 검사 + Redis 링버퍼. 분산 락 없이는 정원이 새어 나간다 |

---

## 4. 현재 구현 상태

### 인프라

- **Flyway 도입** — 엔티티에서 뽑은 DDL을 `V1__init.sql` 베이스라인으로. `ddl-auto`를 `update`/`none` → `validate` 로 통일. 통합테스트도 Flyway를 타므로 마이그레이션 자체가 검증된다
- **캐시 포트 이행** — Spring `@Cacheable`/`CacheManager` 전면 제거, `core.CacheProvider` 로 교체
- **Redis pub/sub 팬아웃** — `MessageBroadcaster` 포트 + `RedisMessageBroadcaster`. 인메모리 STOMP 브로커는 자기 JVM 세션에만 닿아 다중 인스턴스에서 조용히 깨진다
- **Lettuce 스택 제거** — Redisson만 사용. 쿼리 로거가 관측 대상 0건이었다
- **MongoDB 기동 결합 해소** — `auto-index-creation` 을 끄고 인덱스 생성을 기동 경로 밖 스케줄러로 뺐다. Mongo 가 잠깐 흔들려도 `MongoTemplate` 빈 생성이 컨텍스트 refresh 를 취소시키지 않는다. 회귀 고정은 `app/api` 의 `MongoStartupResilienceTest`
- **`infra/mongo` 신설** — 리포지토리 스캔(`@EnableMongoRepositories`)과 인덱스 초기화기가 `module/chat` 에 얹혀 있던 것을 인프라 계층으로 올렸다. 초기화기는 `MongoMappingContext` 가 아는 `@Document` 전부를 훑어 도메인 엔티티를 알지 않는다

### 모듈

| 모듈 | 상태 |
|---|---|
| `member` | 완료. 기준 모듈. 2단계 캐시, 상태 머신, 접속 기록 배치 동기화 |
| `auth` | 완료. OAuth2, JWT, **1인 1기기** 정책, 쿠키 제거(모바일 전용) |
| `attachment` | 완료. presign → key 확정 흐름 |
| `profile` | 완료. 개인정보(성별·생일·국가)는 `member` 로, 언어는 `lang` 으로 이관 |
| `lang` | 완료. 언어 프로필(모국어·학습언어+레벨) CRUD + `LanguageReader` 구현 |
| `matching` | 완료. 언어 상호보완 추천. 자기 데이터가 없는 조합 모듈이라 2계층이다 |
| `chat` | 완료. 1:1 채팅 전체 (아래 상세) |
| `notification` | 완료. `chat-message-sent` 소비 → 3상태 판정 → 인앱/FCM |
| `follow` | 완료. 팔로우 API + `FollowReader` 구현 + `member-blocked` 소비 → 팔로우 양방향 해제 |
| `block` | 완료. 차단 API + `BlockReader` 구현 + `member-blocked` 발행 |
| `moderation` | 완료. 신고 API + `chat-user-reported` 소비 → `Report` 저장 + 운영자 창구(`/api/v1/admin`, 신고 상태 전이·회원 정지/해제) |
| `echo` | 글·타임라인·좋아요·댓글·해시태그·미디어. `EchoAPI` + `EchoController` 로 `/api/v1/echoes` 아래 12개 엔드포인트 노출. 아웃박스는 여전히 미사용 스캐폴딩이다(§5.4) |
| `wave` | 완료. 음성방 + Redis 링버퍼 휘발성 채팅 |

`interest` 는 재설계 예정, `admin` 은 폐기다. `matching` 은 `lang` 신설로 입력이 생겨 다시 만들었다 — 레벨만 있고 어떤 언어의 레벨인지가 없어 추천이 원리적으로 불가능했던 것이 원래 삭제 사유였다.

### chat 모듈 상세

방 생성·목록·메시지 조회·읽음·첨부(앨범)·나가기·삭제·신고 + WebSocket 실시간.

핵심 설계:

- 메시지는 **Mongo**, 첨부는 문서에 **임베드** → 목록 조회 1회
- 안 읽은 수는 `chat_room_members.unread_count` **비정규화** → 방 목록도 조회 1회
- 정렬·커서는 `created_at` 이 아니라 **방별 `seq`** (인스턴스 간 시계 차이로 순서가 뒤집힘)
- 나가기 = **재입장 정책** (나가도 이전 대화 전부 보임)
- 알림은 **발행 직전**에 "그 방 보는 중인지" 판정 → 보고 있으면 생략
- 이중 쓰기(Mongo→Postgres) 창은 **대사 스케줄러**가 5분마다 복구

### 4개 모듈 재구축 완료 (2026-08-14)

`docs/superpowers/plans/2026-08-14-remaining-modules.md` 기준. 전부 병렬 구현 후 점검 완료.

- **A. notification** — `chat-message-sent` 수신 → 세 상태 판정(그 방 보는 중 / 앱만 켜짐 / 미접속) → 인앱 또는 FCM. `FcmPushSender` 는 Firebase Admin SDK 실사용, `fcm.credentials` 미설정 시 경고 로그만 남기고 무시
- **B. relationship** — 팔로우·차단·신고 API + `chat-user-reported` 수신 → `Report` 저장 (그 뒤 `follow`/`block`/`report` 셋으로 분리됐고, `report` 는 다시 `moderation` 으로 넓어졌다)
- **C. echo** — 트위터형 피드 (글·타임라인·좋아요·댓글·해시태그·이미지)
- **D. wave** — 음성방 + **사라지는 채팅** (Redis 링버퍼만, 저장 안 함)

**팔로우 그래프 연결:** `FollowReader` 포트 신설로 결정(지금은 `follow-api`). follow 가 `FollowReaderImpl` 로 구현하고 echo 가 주입받는다. 이벤트 복제는 팔로우 그래프 사본을 echo 가 들고 있어야 해서 기각. 구현 주입이 없으면 `homeTimeline` 이 503 을 던지도록 명시적으로 실패시킨다.

**WebSocket 구독 인가:** 모듈마다 인터셉터를 달던 구조는 폐기했다. 어느 접두사에도 안 걸리는 목적지를 아무도 검사하지 않아 실제로 뚫렸다. 지금은 `common` 의 `WebSocketSubscriptionGate` 가 모든 SUBSCRIBE 를 받아 `core.SubscriptionAuthorizer` 중 `supports` 가 참인 것에게 묻고 **하나도 없으면 거부한다**(기본 거부). 각 모듈은 `{Domain}SubscriptionAuthorizer` 로 자기 토픽 판정만 선언한다. `WaveWebSocketConfiguration` 의 참여자 검사 인터셉터는 삭제됐고 지금은 엔드포인트 등록만 한다.

### 코드 리뷰 확정 결함 조치 완료

| 결함 | 조치 |
|---|---|
| `JwtAuthenticationFilter` 가 `chain.doFilter` 까지 `catch (Exception)` 으로 감싸 Spring Security 예외 경로를 가로챔 | 인증 수립 구간만 감싸도록 축소. `accessDeniedHandler`/`authenticationEntryPoint` 가 정상 동작 |
| ~~같은 필터의 SecurityContext 스레드 유출~~ | **오진이었다. 조치 금지.** `SecurityContextHolderFilter` 가 이미 `finally` 로 정리한다. 여기서 `clearContext()` 를 넣으면 미인증 요청이 401 대신 403 을 받는 회귀가 난다. 회귀 방지 테스트가 `JwtAuthenticationFilterTest` 에 있다 |
| `GlobalRestControllerAdvice` 핸들러 메서드명 중복 | `handleAccessDeniedException` 으로 리네임 |
| Redisson 타입 검증기에 `java.time.` 누락 | `allowIfSubType("java.time.")` 추가 |
| `ResilientCacheConfiguration` 이 전달받은 TTL 을 무시하고 10분 고정 | 해당 `jitteredWriter` 경로를 제거하고 설정을 단일 빈으로 단순화 |
| Kafka DLT 파티션에 소스 파티션 번호를 그대로 지정 | `-1` 로 바꿔 프로듀서가 동적 분배 |
| Virtual Thread `ConcurrentKafkaListenerContainerFactory` 빈 미선언 | 명시 선언 |
| `MemberRepositoryImpl` 핸들 변경 시 구 핸들 캐시 오염 | 읽기 경로에서 재검증 — 캐시로 찾은 회원의 `handle` 이 요청 키와 다르면 버리고 DB 조회 후 evict |
| `AttachmentRepositoryImpl.deleteAll` N+1 단건 삭제 | `deleteAllInBatch` (연관이 없어 고아 행 위험 없음) |
| `Attachment.key` 유니크 인덱스가 MySQL 3072 byte 한계 초과 | **해당 없음.** PostgreSQL + Flyway 로 확정돼 지적 전제가 사라졌다 |
| 정지/탈퇴 회원이 일반 API 경로를 그대로 통과 | `JwtAuthenticationFilter` 가 매 요청 `MemberReader.findStatus` 로 상태를 확인하고 SUSPENDED/WITHDRAWN 을 403 으로 막는다. `/api/v1/auth/` 는 면제(로그아웃·리프레시는 각자 `requireActive` 로 막힌다) |
| 상태 검사가 보는 캐시를 커밋 전 값이 덮어씀 | read-through 적재를 `Cache.putIfAbsent` 로 바꿔 쓰기 경로만 덮어쓰게 했다. 캐시 히트 시 되쓰기(TTL 무한 갱신)도 제거. 회귀 방지는 `MemberStatusCacheRaceTest` |
| 동시 리프레시가 방금 발급한 토큰을 지움 | 회전을 `compareAndSet` 원자 교체로 바꾸고(교체 뒤 `expire` 로 TTL 재설정), **불일치에 세션을 지우지 않는다.** 거부만 한다. 기기 검사를 토큰 비교보다 앞에 둬 밀려난 기기가 `session-taken-over` 를 받는다. 회귀 방지는 `AuthSessionTest` |
| OAuth2 로그인이 기기 바인딩을 갱신하지 않아 기종 변경 사용자가 1시간마다 401 | 로그인 시작 요청(`/oauth2/authorization/*`)의 `deviceId` 를 `OAuth2DeviceIdFilter` 가 세션에 옮겨 콜백에서 쓴다. 기기 id 를 못 받은 발급은 옛 바인딩을 지워 다음 갱신이 TOFU 로 다시 묶게 한다 |

---

## 5. 남은 작업

**완성도 약 88%** (2026-08-28, 커밋 `15c6473` 기준). 산출 방식은 §5.5.

각 항목에 `file:line` 근거와 "어떤 상황에서 실제로 터지나"를 함께 적는다. 그게 없으면 추측이고, **추측으로 고치면 회귀가 난다** — 이 저장소에서 `SecurityContextHolder` 오진을 반영했다가 미인증 요청이 401 대신 403 을 받은 적이 있다.

### 5.1 우선순위 높음

1. **`Member.verify()` 프로덕션 호출자가 0건이다 — 상태 머신이 절반만 배선됐다**
   가입 직후 `CREATED` 를 `ACTIVE` 로 올리는 경로가 아예 없어 **모든 실사용 회원이 영구히 `CREATED`** 다. `JwtAuthenticationFilter.kt:80` 주석이 "여기서 막으면 신규 가입자가 전부 잠긴다"고 명시하며 `CREATED` 를 차단 대상에서 뺀 이유가 이것이다. `MemberService.verify(id)` 래퍼도 테스트 밖 호출부가 없다.
   → **언제 `ACTIVE` 가 되는지 제품 결정이 먼저다.** 프로필 초기 설정 완료 시점인지(`member.init.incomplete` 키가 번들에 있는 걸 보면 원래 설계로 보인다), 가입 즉시인지, `CREATED` 를 없앨지.

2. **FCM 푸시 제목이 i18n 키 원문으로 OS 배너에 그대로 렌더된다**
   `NotificationService.kt:82,98` 이 `title` 에 메시지 키(`notification.chat-message.title`, `notification.member-followed`)를 넣는다. **인앱 브로드캐스트에는 맞는 설계다** — 클라이언트가 키를 받아 번역한다. 그런데 같은 값이 `:66` `push.send(...)` → `FcmPushSender.kt:44` `.setNotification(...)` 으로 들어가고, 그렇게 만든 FCM 메시지는 **OS 가 앱 코드 개입 없이 배너를 그린다.** 번역할 기회가 없다.
   → 서버가 수신자 언어로 렌더할지(`Member.locale` 을 알림 모듈이 알아야 하는데 `MemberReader` 에 없다), `data-only` 푸시로 바꿔 클라이언트가 그릴지 결정 필요. 후자는 iOS 백그라운드 전달 보장이 약해진다.

3. **`application-production.yml` 이 플레이스홀더 상태다**
   `:24,26,72,76,79` 에 DataSource·프론트엔드 URL·CORS 오리진·S3 설정이 TODO 로 남아 있다. production 프로필로는 기동 불가 또는 오설정 기동.

4. **`interest` 재설계** — 사용자가 직접 설계 예정. 붙으면 `MatchScorer` 에 가중치 항을 하나 더한다
5. **`matching` 이 관심사를 아직 못 본다** — 언어·차단·팔로우·접속만 본다. `interest` 가 4번에서 나오면
   `MatchScorer.score` 에 항을 추가한다. 지금 구조는 그걸 전제로 점수 계산을 한 클래스에 몰아 뒀다.
   **`lastAccessedAt` 가점(최근 7일 접속 +3)도 아직 없다** — `MemberReader.ProfileInfo` 에 그 필드가 없고,
   계약을 그 항목 하나 때문에 넓히지 않았다. 필요해지면 `Member.audit` 을 노출하는 방식부터 정해야 한다

6. **`MemberWithdrawnEvent` + 탈퇴 시 토큰 전면 무효화**
   `member-api` 에는 `MemberCreatedEvent`, `MemberHandleChangedEvent` 뿐이다. 잔여 액세스 토큰은 상태 검사 필터가 매 요청 막지만 **리프레시 토큰은 그대로 남는다.** 탈퇴 이벤트 발행 → auth 가 리프레시 토큰 삭제 + 잔여 액세스 토큰 블랙리스트 등록.

### 5.2 중간

7. **앱 전체의 `@Scheduled` 가 WebSocket 하트비트 스레드풀을 공유한다**
   `MainApplication.kt:15` 의 `@EnableScheduling` 이 `TaskScheduler` 빈을 지정하지 않는데, 컨텍스트에 있는 유일한 `TaskScheduler` 가 `ChatWebSocketConfiguration` 의 `@EnableWebSocketMessageBroker` 가 STOMP 하트비트용으로 노출하는 `messageBrokerTaskScheduler`(스레드명 `MessageBroker-*`)뿐이다. Spring 은 그럴 때 **`@Scheduled` 전부를 그 빈에 위임**한다. 아웃박스 폴러(2초 × 3모듈)·캐시 헬스(5초)·`ChatReconciler`(5분)·접속 동기화(10분)가 전부 하트비트용 풀 위에서 돈다.
   → **정상 상태에서는 무증상이다.** Mongo·Redis 가 느려져 스케줄러 하나가 스레드를 30초씩 잡으면 관계없는 아웃박스 발행까지 밀린다. `@DistributedLock(throwOnFailure = false)` 는 **락 획득 실패만** 넘기지 본문 블로킹은 못 막는다. 애플리케이션용 `TaskScheduler` 를 별도 등록해 분리한다. 장애 증폭기이지 상시 결함은 아니다.

8. **`MessageDeduplicator` 표시가 처리 성공 *전*에 남아 강제 종료 시 유실된다**
   `MessageDeduplicator.kt:31-38` 이 한계를 직접 서술한다 — 되돌림은 같은 JVM 에서 `Exception` 이 잡혔을 때만 돈다. `Error`(OOM)나 SIGKILL·OOMKilled 로 죽으면 표시만 남고 오프셋은 미커밋이라, 재기동 후 재배달이 "중복"으로 걸러져 TTL(1시간)까지 유실된다. 그레이스풀 셧다운은 in-flight 를 기다리므로 정상 배포로는 안 터진다.
   → 표시를 **처리 성공 후**로 옮기면(전형적 idempotent-consumer) 유실 경로가 사라지고 `release` 자체가 불필요해진다. 대신 리밸런싱 중 겹치는 재배달을 못 막는다. 이 설계가 선언한 우선순위("중복 < 유실")와는 그쪽이 일치한다.

9. **Swagger `{Domain}API` 인터페이스 누락** — `ProfileController`(7개 엔드포인트), `AuthController`(2개). `AttachmentController`(1개, 로컬 전용)는 면제 가능.

10. **`ExceptionResponse` 포맷 확장** — 지금 `status` + `message` 뿐. `code`, `timestamp`, `path`, `traceId`(MDC) 추가 + TraceId 주입 필터
11. **통합 테스트 부재** — `echo`, `wave`, `attachment`, `auth` 는 Testcontainers 통합 테스트가 없다. `app/api` E2E 가 전 모듈을 기동하므로 스키마 정합만은 검증된다.

### 5.3 낮음 / 정책 결정 필요

12. **차단 상대의 팔로워/팔로잉 *수*는 프로필로 그대로 나간다** — 목록은 403 인데 숫자는 열려 있다. 일관성 문제이자 제품 판단.
13. **리프레시 토큰 재사용 감지(RTR)** — 1인 1기기 정책이라 새 기기 로그인 시 기존 세션이 끊긴다(`auth.session-taken-over`). 무효화된 리프레시 토큰으로 재발행을 시도하면 전 세션 강제 파기까지 갈지 결정 필요
    **현재 동작**: 무효 토큰은 401 로 거부만 하고 세션은 건드리지 않는다. 회전 때문에 "저장값과 다르다" 가 탈취뿐 아니라 "다른 요청이 방금 갱신했다" 는 뜻이기도 해서, 삭제로 처리하면 정상 사용자가 동시 갱신만으로 잘렸다. 재사용을 **감지해도 조치하지 않는** 상태이므로, 조치를 넣으려면 정상 동시 갱신과 진짜 재사용을 가르는 기준(직전 토큰 grace window, 토큰별 jti 등)을 먼저 정해야 한다.
14. **회원 검색 API** — handle 부분 일치 검색이 없다. 팔로우 기능이 생겨 **사람을 찾을 방법이 필요해졌다** — 지금은 정확한 handle 을 알아야 한다.
    **기반(pg_trgm + unaccent)은 만들었다** — `infra/rdb` 의 `V10__trgm_search_base.sql`(확장 + `f_unaccent` IMMUTABLE 래퍼)과 `StringPathSearch.kt`(`StringPath.search()` QueryDSL 확장, `MIN_SEARCH_LENGTH = 2`). 실제 테이블(`members.handle` 등)에 GIN 인덱스를 걸고 검색 API 를 붙이는 건 아직이다 — **호출자가 0건**이라 이 기반이 죽은 스캐폴딩으로 남지 않으려면 다음 작업이 필요하다. 컬럼 적용 시 `create index using gin (f_unaccent(컬럼) gin_trgm_ops)` 를 Flyway 로 만들어야 함수 인덱스를 탄다.
    **운영 배포 전 확인 필요**: `V10` 의 `create extension pg_trgm/unaccent` 는 슈퍼유저(또는 AWS RDS `rds_superuser`) 권한이 필요하다. 로컬·Testcontainers 는 슈퍼유저 계정이라 통과하지만, 운영 Flyway 실행 계정이 최소 권한이면 `permission denied to create extension` 으로 배포가 그 자리에서 죽는다. 마스터 계정으로 두 확장을 미리 설치해 두거나 Flyway 계정에 권한을 부여해야 한다.
15. **신고 원본 내용 조회** — 운영 API 는 메타데이터(`sourceType`/`sourceId`)까지만 준다. 운영자가 실제 글·채팅 내용을 보려면 `echo-api` 에 `PostReader`, `chat-api` 에 `MessageReader` 를 신설해야 한다(채팅은 MongoDB 다). 상태 전이(접수→검토→조치)와 정지 조치는 `moderation` 이 갖췄다
16. **소셜 계정 추가 연동 / 연동 해제** — Google ↔ Apple 교차 연동. 현재는 가입 시 provider 하나에 고정
17. **마케팅 수신 동의 *일시*** — `agreedMarketingReceive`(Boolean) 만 있고 시각이 없다. 법무 확인 후 `MemberAudit.agreedMarketingAt` 추가
18. **wave 채팅 신고 증거** — 휘발성이라 신고 시 스냅샷을 뜰지, 뜬다면 보존 기간을 얼마로 할지. **안 뜨면 신고를 받아도 근거가 없다**
19. **팔로워 수 비정규화 시점** — 지금은 COUNT 쿼리다(항상 정확, 백필 불필요). 팔로워 수십만 계정이 생기면 재검토하고, 그때는 `block()` 이 팔로우를 양방향으로 끊는 경로까지 카운터를 내려야 한다
20. **`chat_messages` 시간 파티셔닝** — Mongo 로 옮겨 당장은 불필요. Postgres 에 대용량 테이블이 생기면 재검토
21. **내 언어를 바꿔도 matching 후보 캐시가 그대로다**
    `LanguageService.replace` 는 `member_languages` 만 갈아끼우고 `matching` 의 후보 캐시(`match:candidates:{회원id}`, TTL 10분)를 건드리지 않는다. `lang` 은 모듈 경계상 그 캐시의 존재를 알지 못하고, 알게 만들면 `lang` 이 `matching` 을 참조하게 돼 경계가 뒤집힌다.
    → **터지는 경로:** 추천을 한 번 조회해 후보가 캐시된 직후 `PUT /api/v1/langs/me` 로 학습언어나 모국어를 바꾸면, 클라이언트가 `refresh=true` 를 명시하지 않는 한 최대 10분 동안 **이전 언어 기준으로 뽑힌 후보**가 그대로 나온다. 그 사이 `matchedPairs` 는 새 언어로 다시 계산되므로 추천 근거가 빈 배열인 회원이 목록에 남는다 — 사용자에게는 "왜 추천됐는지 알 수 없는 사람"으로 보인다.
    → **지금은 트레이드오프로 받아들인다.** 10분 TTL 과 당겨서 새로고침(`refresh=true`)이 실사용 경로를 덮고, 잘못된 것은 순서일 뿐 차단·정지 같은 안전 필터가 새는 것은 아니다. 고치려면 `lang` 이 `MemberLanguagesChangedEvent` 를 발행하고 `matching` 이 그것을 컨슘해 캐시를 evict 하는 이벤트 기반 무효화가 필요한데, **아웃박스 테이블과 컨슈머를 새로 다는 비용이 이 증상에 비해 크다.** 캐시 TTL 을 늘리거나 언어 변경이 잦아지면 그때 다시 본다.

22. **정지 만료 조회가 인덱스를 못 타고 Seq Scan 을 한다**
    `MemberSuspendHistoryRepositoryImpl.findExpired` 는 `where is_released = false and release_at <= now` 로 조회하는데, 이 테이블의 유일한 인덱스 `IDX_MEMBER_SUSPEND_RELEASED` 는 `(member_id, is_released)` 순서다. **선두 컬럼이 `member_id` 라 이 조건으로는 레인지 스캔을 못 탄다.** 같은 인덱스를 쓰는 `findOpen(memberId)` 는 정상적으로 탄다.
    → **터지는 경로:** 배치가 10분마다 도는데 매 주기 `member_suspend_history` 전체를 훑는다. 운영 이력이 수천 건 수준이면 무시할 만하지만, 정지 이력은 **닫힌 행도 지우지 않고 계속 쌓이는 구조**라 단조 증가한다. 수만 건을 넘으면 10분마다 도는 풀스캔이 되고, 그 시점에 정작 만료 대상은 여전히 몇 건뿐이라 비용 대비 소득이 없다.
    → `(is_released, release_at)` 복합 인덱스나 `where is_released = false` 부분 인덱스를 Flyway 로 추가한다. **부분 인덱스 쪽이 낫다** — 닫힌 행이 인덱스에서 아예 빠져 크기가 "현재 열린 정지 수"에 묶인다. 지금 붙이지 않은 건 행이 없는 상태에서 재는 실행계획이 무의미하고, 규모가 오기 전까지는 Seq Scan 이 오히려 싸기 때문이다.

### 5.4 정리 대상 (기능 영향 없음)

- **echo 아웃박스 스캐폴딩** — `EchoOutBox`·`EchoOutBoxHistory`·`EchoOutBoxRepository` 와 테이블이 있으나 쓰는 코드도 스케줄러도 없다. `echo-api` 의 DTO 2종도 발행하는 코드가 없다
- **`member-created` / `member-handle-changed` 컨슈머 부재** — 발행되지만 듣는 사람이 없다
- **`EchoRepository.aggregateDailyStats` 호출자 없음** — `hashtag_daily_stat` 이 영원히 비어 있다
- **`Post.reportCount` 증가시키는 코드 없음** — 항상 0
- **`ResilientCacheProvider.kt:38` 헬스 스케줄러에 `@DistributedLock` 없음** — **의도적이다.** 노드별 로컬 폴백 상태를 각자 판단해야 한다. 규약(§5 "`@Scheduled` 에는 반드시 `@DistributedLock`")의 유일한 예외이니 "누락"으로 보고 붙이지 마라

### 5.5 완성도 산출 방식

모듈/컴포넌트별 점수를 "이 프로젝트가 서비스로 성립하는 데 그 조각이 차지하는 비중"으로 가중 평균한다. 모듈 점수는 5개 축의 가중합이다.

| 축 | 가중치 | 판정 기준 |
|---|---|---|
| 4계층 구조 | 10% | 디렉터리 존재 + 의존 방향 준수 |
| HTTP 엔드포인트 노출 | 30% | 서비스 로직이 실제로 외부에서 호출 가능한가 |
| 도메인·서비스 로직 완성도 | 25% | 유스케이스가 끝까지 이어지는가(이벤트 발행→소비 포함) |
| 테스트 | 20% | 단위 + Testcontainers 통합 존재 여부 |
| 인프라 결선 | 15% | Flyway 정합, 아웃박스 발행, 인가 검사 |

| 모듈 | 점수 | 가중치 | | 모듈 | 점수 | 가중치 |
|---|---|---|---|---|---|---|
| member | 97% | 12 | | attachment | 88% | 5 |
| chat | 92% | 14 | | common | 92% | 6 |
| auth | 92% | 10 | | app/api | 78% | 6 |
| follow/block/moderation | 92% | 9 | | infra/rdb | 95% | 4 |
| echo | 70% | 9 | | core | 96% | 3 |
| profile | 84% | 8 | | infra/redis | 96% | 3 |
| notification | 85% | 7 | | infra/kafka | 80% | 2 |
| wave | 88% | 7 | | | | |

가중 합계 **92.89 / 105 = 88.5% → 약 88%**

직전 갱신(`552225c`, 91.94)에서 오른 곳과 근거:

| 모듈 | 변화 | 근거 |
|---|---|---|
| chat 90→92, infra/rdb 93→95 | 인프라 결선 | `5320b99` — `OutBoxHistoryCleaner` 베이스와 chat 아카이버. 완료 행이 영원히 남던 것과 히스토리 무한 증가가 둘 다 해소 |
| attachment 85→88 | 4계층 구조 | `#18` — domain 이 웹 타입을 참조하던 유일한 모듈이었다 |
| profile 80→84 | 도메인·서비스 로직 | `#19` — 입력 검증 부재로 500 이 나던 경로 차단 |
| common 90→92 | 도메인·서비스 로직 | `#19` — 검증 실패 메시지가 i18n 키 원문으로 나가던 것 해소 |

**다음 갱신 때 이 방식과 가중치를 그대로 써라.** 방식을 바꾸면 이전 숫자와 비교가 안 된다.

### 5.6 정책 변경으로 폐기된 항목

되살리려면 정책부터 다시 논의해야 한다. 모르고 다시 착수하는 걸 막으려고 남긴다.

| 폐기 항목 | 사유 |
|---|---|
| 탈퇴 회원 개인정보 익명화 / 30일 유예 후 삭제 배치 | **의도적으로 하지 않는다.** 탈퇴 후 재가입해 같은 문제를 반복하는 회원을 추적해야 해서 계정 기록을 영구 보존한다 (`Member.withdraw` KDoc 참조) |
| 멀티 디바이스 세션 관리 / 기기 목록 / 원격 로그아웃 | **1인 1기기 정책**으로 확정. 기기 목록이라는 개념 자체가 없다. `MemberAudit.lastDeviceId` 하나로 끝난다 |
| Redis Stream DLQ (`autoClaim` 재시도 추적) | Redis Stream 을 안 쓴다. 메시징은 Kafka 로 통일했고 DLT 가 그 역할을 한다 |
| `Member.status` 라이프사이클 도입 | 완료 (`CREATED`/`ACTIVE`/`SUSPENDED`/`WITHDRAWN` + `suspend`/`unsuspend`/`withdraw`/`requireActive`) |
| `Member.profileImageUrl`, 약관 동의 이력, `getMe` 통합 조회 | 완료 (`Member.imageUrl`, `MemberAudit.agreedTermsAt`, `MemberMeResponse`) |
| Kafka 컨슈머 짝 맞추기 | 완료. `chat-message-sent` → notification, `chat-user-reported` → moderation, `member-blocked` → follow |
| 신고 처리 워크플로 / 운영자 창구 | 완료. `moderation` 이 신고 상태 전이와 회원 정지·해제를 `/api/v1/admin` 으로 낸다. 삭제된 `admin` 모듈을 되살리지 않고 신고 소유 모듈이 갖는다 |
| 기간 정지 만료 해제 | 완료. `MemberSuspendReleaseScheduler`. `releaseAt` 을 읽는 코드가 없어 기간 정지가 영구 정지였다 |

---

## 6. 검증 기준

```bash
./gradlew build          # 전체 빌드 + 테스트
```

- 통합테스트는 Testcontainers(Postgres · Redis · Mongo)를 띄운다. Docker 필요
- `ddl-auto: validate` 라 마이그레이션과 엔티티가 어긋나면 **기동 시점에** 잡힌다
- 신규 i18n 키는 `common/src/main/resources/messages_*.properties` **12개 전부**(ko, ja, en, de, es, fr, pt, id, ru, vi, zh_CN, zh_TW)에 있어야 한다. 누락은 조용히 넘어가고 키 문자열이 그대로 사용자 응답에 노출된다

  ```bash
  for f in common/src/main/resources/messages_*.properties; do echo "$(basename $f) $(grep -c '^[a-z].*=' $f)"; done
  ```

  전부 같은 수여야 한다

---

## 7. 코드 규약

모듈 구조, 계층 의존 방향, 네이밍, 주석, 코틀린 스타일, 그리고 **반복해서 터진 함정 목록**은 [`CLAUDE.md`](CLAUDE.md) 에 있다.

엔티티 작성법, 포트·어댑터 구조, 트랜잭션·예외 처리, Swagger 분리, 실시간(WebSocket) 인증·인가, 비동기·마이그레이션·i18n, 테스트 작성법, 새 모듈 체크리스트는 [`module/CLAUDE.md`](module/CLAUDE.md) 에 있다.

새 코드를 쓰기 전에 읽는다.

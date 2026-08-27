# Langlez Backend 코드 감사 (AUDIT)

감사일: 2026-08-26 · 대상: `/Users/hj/project/langlez/server`, 브랜치 `main`, 커밋 `960cc4f`
방식: 읽기 전용 정적 분석 + `./gradlew build` 실제 실행. 이 문서 외 파일은 수정하지 않았다.

---

## 1. 전체 완성도: **약 80%**

### 산출 방식

모듈/컴포넌트별 완성도를 매기고, "이 프로젝트가 서비스로 성립하는 데 그 조각이 차지하는 비중"으로 가중 평균했다.

모듈 단위 점수는 아래 5개 축의 가중합이다.

| 축 | 가중치 | 판정 기준 |
|---|---|---|
| 4계층 구조(api/application/domain/infrastructure) | 10% | 디렉터리 존재 + 의존 방향 준수 |
| HTTP 엔드포인트 노출 | 30% | 서비스 로직이 실제로 외부에서 호출 가능한가 |
| 도메인·서비스 로직 완성도 | 25% | 유스케이스가 끝까지 이어지는가(이벤트 발행→소비 포함) |
| 테스트 | 20% | 단위 + Testcontainers 통합 존재 여부 |
| 인프라 결선(스키마·아웃박스·인가) | 15% | Flyway 정합, 아웃박스 발행, 인가 검사 |

컴포넌트 가중치(합 105 → 정규화):
member 12 · chat 14 · auth 10 · relationship 9 · echo 9 · profile 8 · notification 7 · wave 7 · attachment 5 · common 6 · app/api 6 · infra:rdb 4 · core 3 · infra:redis 3 · infra:kafka 2

가중 합계 **83.7 / 105 = 79.7% → 약 80%**

### 한 줄 요약

기반(스키마·캐시·락·아웃박스 베이스·보안 필터·i18n)은 완성도가 높고 테스트로 뒷받침된다.
깎아먹는 곳은 **echo·notification 모듈이 HTTP로 노출되지 않은 것**과 **relationship 아웃박스가 발행되지 않는 것** 두 가지다. 이 셋만 채우면 90% 대로 올라간다.

---

## 2. 모듈별 완성도

| 모듈 | % | 근거 | 미비점 |
|---|---|---|---|
| `module/member` | 95% | 4계층 완비, 엔드포인트 9개, `MemberAPI` Swagger 분리, 소스 29 / 테스트 10(통합 포함), 테스트 68건, 아웃박스 send + history 스케줄러 둘 다 존재 | i18n 키 5개 누락(§3-D1), `MemberWithdrawnEvent` 부재 |
| `module/chat` | 88% | 엔드포인트 9개, `ChatAPI` 분리, 테스트 78건(WS 통합 · Mongo 통합 · reconciler 포함), 아웃박스 send 스케줄러 + `published` 플래그 발행기, WS 구독 인가 존재 | WS 구독 인가 우회 경로(§3-C1), `ChatOutBoxHistoryScheduler` 없음(§3-B2) |
| `module/auth` | 85% | OAuth2 성공 핸들러 + refresh/logout 2개 엔드포인트, 1인 1기기 바인딩 구현, 테스트 30건, fail-open 방어 주석·코드 모두 있음 | `infrastructure` 계층 없음(자체 영속성 없어 무해), `AuthAPI` Swagger 인터페이스 없음, 정지/탈퇴 시 토큰 무효화 미구현(§3-A3) |
| `module/wave` | 85% | 엔드포인트 7개, `WaveAPI` 분리, 테스트 31건, Redis 링버퍼 채팅, WS 구독 인가 별도 구현, `wave_messages` 폐기(V5)와 코드 일치 | Testcontainers 통합 테스트 없음, WS 우회 경로 공통(§3-C1) |
| `module/attachment` | 85% | presign→attach(key 기반) 흐름 완성, 경로 이탈 방어 있음, 로컬 업로드 컨트롤러 `@Profile("!production")`, 테스트 13건 | `domain` 이 `HttpStatus`/`LanglezException` 참조(§3-E2) |
| `module/profile` | 80% | 엔드포인트 7개, 테스트 35건 + 동시성 통합 테스트, 방문수 Redis HLL → 배치 flush 설계 | `ProfileAPI` Swagger 없음, 수정 요청에 검증 전무(§3-B4), 와일드카드 import |
| `module/relationship` | 75% | 엔드포인트 8개, `RelationshipAPI` 분리, 테스트 32건 + 통합, 신고 중복 방지 존재, `chat-user-reported` 컨슈머 동작 | **아웃박스 발행 스케줄러 없음(§3-A2)**, 히스토리 아카이버 없음, 이벤트 DTO 가 `core` 밖에 있음(§3-E1) |
| `module/notification` | 55% | 컨슈머 + 3상태 판정(보는 중/온라인/오프라인) + FCM 연동 완성, 테스트 13건 + 통합 | **컨트롤러 없음 → `list`/`markRead` 도달 불가(§3-A4)**, 컨슈머 멱등성 없음(§3-B1), 알림 토픽 구독 인가 없음(§3-C2) |
| `module/echo` | 45% | 서비스 12개 메서드(글·타임라인·좋아요·댓글·해시태그·첨부) 구현, 소스 24, 좋아요 카운터는 DB 단일 UPDATE | **컨트롤러 없음 → 전체 도달 불가(§3-A1)**, 아웃박스 전부 미사용 스캐폴딩, 통합 테스트 없음, `aggregateDailyStats` 호출자 없음, `Post.reportCount` 미사용 |
| `core` | 95% | 포트 9종 + 이벤트 DTO. 순수 인터페이스라 테스트 불요 | `TokenBlacklist` 개명 TODO, 회원 탈퇴 이벤트 부재 |
| `common` | 85% | 보안 설정 · JWT 필터 · `@MemberId` 리졸버 · i18n 12개 번들 · 전역 예외 처리, 테스트 13건 | i18n 키 5개 누락(§3-D1), `ExceptionResponse` 가 `status`+`message` 뿐 |
| `infra/rdb` | 90% | 아웃박스 베이스(가상 스레드 + 세마포어 `by lazy` 함정 방어), Flyway, QueryDSL | 히스토리 테이블 정리 배치 없음(§3-B3) |
| `infra/redis` | 95% | Resilient 캐시(로컬 폴백 + 헬스 프로브), 분산 락, pub/sub 브로드캐스터, 테스트 17건 | 헬스 스케줄러에 `@DistributedLock` 없음(의도적, §3-E4) |
| `infra/kafka` | 80% | 프로듀서/컨슈머 설정 + DLT, 테스트 2건 | 소스 1파일. 재시도·DLT 정책 테스트 얕음 |
| `app/api` | 70% | 전 모듈 조립, `@EnableScheduling`/`@EnableAsync`, E2E 테스트 1개(전체 컨텍스트 기동 = Flyway validate 검증) | `application-production.yml` 이 TODO 플레이스홀더 상태(§3-B5) |
| `infra/mongo` | — | **소스가 하나도 없다.** `build/` 잔재만 남은 빈 디렉터리라 `settings.gradle.kts` 에도 등록되지 않는다 | 삭제 대상(§3-E5) |

---

## 3. 결함 목록

추측을 배제하고 코드로 확인된 것만 적었다. 런타임 재현까지 한 항목과 정적 확인만 한 항목을 구분했다.

### A. 치명 (기능이 도달 불가하거나 데이터가 유실됨)

**A1. `module/echo` 에 컨트롤러가 없다 — 모듈 전체가 HTTP로 도달 불가**
`module/echo/src/main/kotlin/com/langlez/echo/api/` 에는 `request/`·`response/` DTO 4개만 있고 `@RestController` 가 하나도 없다. 저장소 전체에서 `@RestController` 는 8개이며 echo 는 그중 없다.
- 증상: `EchoService` 의 12개 메서드(`createPost`, `homeTimeline`, `memberTimeline`, `hashtagTimeline`, `getPost`, `like`, `unlike`, `comment`, `listComments`, `deleteComment`, `deletePost`, `presignUpload`)가 전부 호출 경로 없음. 응답 DTO `EchoPostResponse`/`EchoCommentResponse` 도 미사용.
- 수정 방향: `EchoAPI`(Swagger 인터페이스) + `EchoController` 를 `/api/v1/echoes` 로 추가. `@MemberId` 로 회원 식별, 목록 엔드포인트에는 `size.coerceIn(1, MAX)` 상한.

**A2. `relationship` 아웃박스에 발행 스케줄러가 없다 — 이벤트가 영구 적재만 됨**
`module/relationship/src/main/kotlin/com/langlez/relationship/api/RelationshipEventListener.kt:27` 이 `member-followed` 를 `RelationshipOutBox` 로 저장하지만, `module/relationship/.../infrastructure/outbox/` 에는 `RelationshipOutBox.kt`, `RelationshipOutBoxHistory.kt` 만 있고 `OutBoxProcessor` 를 상속한 스케줄러가 없다. (chat/member 는 각각 `ChatOutBoxScheduler`, `MemberOutBoxScheduler` 보유)
- 증상: 팔로우가 일어날 때마다 `relationship_event_outbox` 에 행이 쌓이고 Kafka 로는 한 건도 나가지 않는다. 테이블이 무한 증가하며, 저장소 전체에 `member-followed` 컨슈머도 없어 이벤트는 완전히 사장된다.
- 수정 방향: `MemberOutBoxScheduler` 를 그대로 복사해 `RelationshipOutBoxScheduler` 추가(`@Scheduled` + `@DistributedLock(prefix = "lock:relationship-outbox")`). 소비처가 없다면 이벤트 발행 자체를 걷어내는 쪽이 더 정직하다.

**A3. 정지/탈퇴 회원의 액세스 토큰이 만료까지 그대로 살아 있다**
`common/src/main/kotlin/com/langlez/filter/JwtAuthenticationFilter.kt:45-62` 는 블랙리스트 · 토큰 타입 · 서명만 본다. `Member.status` 는 확인하지 않는다. 저장소 전체에서 `SUSPENDED`/`WITHDRAWN` 문자열이 `common`·`app`·`module/auth` 의 운영 코드에 한 번도 등장하지 않는다. 상태 검사(`Member.requireActive()`)는 `module/auth/.../AuthService.kt:60`(로그인)과 `:100`(refresh) 두 곳뿐이다.
- 증상: 정지·탈퇴 직후에도 이미 발급된 액세스 토큰으로 모든 일반 API 를 계속 쓸 수 있다. 리프레시 토큰도 지워지지 않는다.
- 수정 방향: PLAN.md 4.1-3/4 그대로. `JwtAuthenticationFilter` 뒤에 상태 검사 필터 + `MemberWithdrawnEvent` → auth 가 리프레시 토큰 삭제 + 잔여 액세스 토큰 블랙리스트 등록.

**A4. `module/notification` 에 컨트롤러가 없다 — 알림 조회/읽음 처리 도달 불가**
`module/notification/src/main/kotlin/com/langlez/notification/api/` 에 `NotificationConsumer.kt` 하나뿐이다.
- 증상: `NotificationService.list(memberId, size, cursor)`, `markRead(memberId, id)` 가 호출 경로 없음. `notification.not-found`, `notification.forbidden` i18n 키도 사용되지 않는다. 사용자는 실시간 푸시만 받고 알림함을 열 수 없다.
- 수정 방향: `NotificationAPI` + `NotificationController` (`GET /api/v1/notifications`, `PATCH /api/v1/notifications/{id}/read`).

### B. 높음

**B1. Kafka 컨슈머에 멱등성 방어가 없다 (notification)**
`module/notification/src/main/kotlin/com/langlez/notification/api/NotificationConsumer.kt:19-22` → `NotificationService.onChatMessage` → `notify()` 는 무조건 `repo.save(Notification(...))` 후 FCM 전송(`NotificationService.kt:44-67`). 중복 검사가 없다.
- 증상: Kafka 는 at-least-once 다. 리밸런싱·오프셋 커밋 실패로 재전달되면 알림 행이 두 번 쌓이고 FCM 푸시도 두 번 간다.
- 참고: `relationship` 쪽은 `RelationshipService.report` 가 `repo.existsReport(...)` 로 걸러 부분적으로 멱등이다(`RelationshipService.kt:99`). 다만 유니크 제약이 없는 check-then-insert 라 동시 재전달에는 취약하다. PLAN.md 4.2-5 가 relationship 도 무방비라고 적은 것은 현재 코드보다 비관적이다.
- 수정 방향: PLAN.md 4.2-5 그대로. Redis `SETNX` 기반 `messageId` 중복 검사 AOP. 병행해서 `reports` 에 `UNQ_REPORT_...` 유니크 제약 추가.

**B2. `chat`·`echo`·`relationship` 아웃박스에 히스토리 아카이버가 없다**
`OutBoxHistoryProcessor` 를 상속한 클래스는 저장소 전체에서 `MemberOutBoxHistoryScheduler` 하나뿐이다(`module/member/.../outbox/MemberOutBoxHistoryScheduler.kt:12`). `ChatOutBoxHistory`, `EchoOutBoxHistory`, `RelationshipOutBoxHistory` 엔티티는 있으나 채우는 쪽이 없다.
- 증상: `chat_event_outbox` 는 완료 행이 영원히 남는다(`OutBoxProcessor.send` 는 `complete()` 후 `repo.save` 만 하고 삭제하지 않는다 — `infra/rdb/.../OutBoxProcessor.kt:37-50`). 조회 인덱스가 커지며 `fetch` 가 점점 느려진다.
- 수정 방향: `MemberOutBoxHistoryScheduler` 복제.

**B3. 아웃박스 히스토리 테이블을 비우는 배치가 아예 없다**
`infra/rdb/src/main/kotlin/com/langlez/rdb/outbox/OutBoxHistoryProcessor.kt:22-38` 은 완료 건을 `*_outbox_history` 로 옮기기만 한다. 저장소 전체에 `*_outbox_history` 를 삭제/파티션 드롭하는 코드가 없다.
- 증상: `member_outbox_history` 가 무한 증가.
- 수정 방향: PLAN.md 4.2-6 그대로. 보존 기간(예: 30일) 기준 `@Scheduled` + `@DistributedLock` 삭제 배치.

**B4. 프로필 수정 요청에 검증이 전혀 걸려 있지 않다**
`module/profile/src/main/kotlin/com/langlez/profile/api/ProfileController.kt:19-25` 의 `@RequestBody request: ProfileRequest.Update` 에 `@Valid` 가 없고, `module/profile/src/main/kotlin/com/langlez/profile/api/ProfileRequest.kt:15-24` 의 `Update` 에는 Bean Validation 어노테이션이 하나도 없다.
- 증상: `bio`/`goal`/`want` 길이 제한이 없다. DB 컬럼은 `varchar(1000)`(`V1__init.sql:176-178`)이라 1000자까지는 그냥 저장되고, 초과하면 `DataIntegrityViolationException` 으로 500 이 난다. i18n 에 `validation.member.bio.size`(200자), `validation.member.goal.size`(500자), `validation.member.want.size`(500자) 가 준비돼 있는데 참조하는 코드가 없다 — 의도한 제한이 사문화됐다.
- 수정 방향: `Update` 에 `@field:Size(max = ..., message = "validation.member.bio.size")` 등을 붙이고 컨트롤러에 `@Valid` 추가. 상한값은 엔티티 companion 상수로 한 곳에서만 정의(CLAUDE.md §3 규약).

**B5. `application-production.yml` 이 플레이스홀더 상태다**
`app/api/src/main/resources/application-production.yml:24, 26, 72, 76, 79` 에 DataSource 값, 프론트엔드 URL, CORS 허용 오리진, S3/CloudFlare 설정이 전부 TODO 로 남아 있다.
- 증상: production 프로필로는 기동 불가 또는 오설정 기동.
- 수정 방향: 환경변수 주입으로 전환.

### C. 중간 (인가)

**C1. WebSocket 구독 인가가 접두사에 걸리지 않는 목적지에 대해 열려 있다**
`module/chat/.../ChatWebSocketConfiguration.kt:112` — `if (!destination.startsWith(ROOM_TOPIC_PREFIX)) return null` (인가 통과)
`module/wave/.../WaveWebSocketConfiguration.kt:57` — `if (!destination.startsWith(ROOM_TOPIC_PREFIX)) return message` (인가 통과)
- 증상: 두 인터셉터 모두 `/topic/chat/room/`, `/topic/wave/` 로 **시작하는** 목적지만 검사한다. 브로커는 `registry.enableSimpleBroker("/topic")`(`ChatWebSocketConfiguration.kt:49`) 로 등록된 심플 브로커이고, Spring 의 `DefaultSubscriptionRegistry` 는 구독 목적지를 `PathMatcher`(기본 `AntPathMatcher`) 패턴으로 취급한다. 따라서 `/topic/**` 처럼 접두사에 걸리지 않는 패턴으로 구독하면 검사를 통과한 뒤 모든 방 메시지를 받게 된다. 코드 주석 자체가 "심플 브로커는 구독 목적지에 별표 와일드카드를 지원"한다고 인정하면서, 방어는 접두사 안쪽에만 걸어 뒀다.
- 확인 수준: 코드 경로로 확인. 실제 STOMP 클라이언트로 `/topic/**` 를 구독해 재현하는 런타임 검증은 **미확인**.
- 수정 방향: 허용 목적지를 화이트리스트 정규식으로 뒤집는다. 아는 패턴(`^/topic/(chat/room|wave)/\d+...$`, `^/topic/notification/\d+$`)에 매치되지 않는 SUBSCRIBE 는 전부 거부.

**C2. 알림 토픽 구독에 인가 검사가 없다**
`module/notification/.../NotificationService.kt:56` 이 `/topic/notification/{memberId}` 로 브로드캐스트한다. 이 접두사를 검사하는 `ChannelInterceptor` 가 저장소에 없다 — chat 인터셉터는 `/topic/chat/room/` 이 아니면 `null` 을 반환해 통과시키고(`ChatWebSocketConfiguration.kt:112`), wave 인터셉터도 `/topic/wave/` 가 아니면 통과시킨다(`WaveWebSocketConfiguration.kt:57`).
- 증상: 인증만 통과한 회원이 `/topic/notification/{남의id}` 를 구독해 타인의 실시간 알림(발신자 id, 방 id, 메시지 미리보기 포함)을 그대로 받는다. CLAUDE.md §13 "인증만 하고 인가 안 함" 함정의 재발.
- 확인 수준: 코드 경로로 확인. 런타임 재현은 미확인.
- 수정 방향: C1 의 화이트리스트에 `^/topic/notification/(\d+)$` 를 넣고 `memberId` 일치를 강제.

### D. 중간 (i18n)

**D1. i18n 키 5개가 12개 번들 전부에 없다**
12개 번들의 키 수는 모두 72개로 같고 키 집합도 완전히 동일하다(ko 기준 diff 0). 문제는 **코드가 쓰는 키가 번들에 없는 것**이다.

| 누락 키 | 사용 위치 |
|---|---|
| `member.handle.cooldown` | `module/member/src/main/kotlin/com/langlez/member/domain/Member.kt:83` |
| `member.handle.invalid` | `Member.kt:84`, `module/member/.../api/request/MemberUpdateHandleRequest.kt:9` |
| `member.handle.duplicated` | `module/member/.../application/MemberService.kt:53`, `:64` |
| `member.already-verified` | `Member.kt:68` |
| `member.already-withdrawn` | `Member.kt:97`, `Member.kt:104` |

- 증상: `GlobalRestControllerAdvice` 는 키를 못 찾으면 키 문자열을 그대로 응답 본문에 담는다. 핸들 변경 실패 시 사용자에게 `"member.handle.cooldown"` 이 그대로 보인다. CLAUDE.md §13 "i18n 키 누락" 함정의 실제 발생 사례다.
- 참고: 번들에 `member.init.handle.invalid`, `member.init.handle.duplicated` 가 있는데 코드는 `member.handle.*`(`init.` 없음)을 던진다. 접두사 리팩터링 때 코드만 바뀌고 번들이 안 따라온 것으로 보인다.
- 반대 방향 미사용 키: `validation.member.address.size` (`Member` 에 address 필드 없음), `notification.not-found`/`notification.forbidden` (§A4 로 도달 불가).
- 수정 방향: 5개 키를 12개 번들 전부에 추가. `member.init.handle.*` 와 `member.handle.*` 중 하나로 통일.

### E. 낮음 (규약 위반 · 정리 대상)

**E1. 도메인 이벤트 DTO 가 `core` 밖에 있다**
`module/relationship/src/main/kotlin/com/langlez/relationship/application/RelationshipEvents.kt:12` 의 `MemberFollowedEvent`. CLAUDE.md §5 는 "도메인 이벤트 DTO 는 `core/event/{domain}/` 에 둔다 — 모듈 간 공유 계약이다"라고 규정한다. `core/src/main/kotlin/com/langlez/core/event/` 에는 chat/echo/member 만 있다.
- 수정 방향: `core/event/relationship/` 로 이동. 어차피 A2 를 고칠 때 소비처가 생긴다.

**E2. `attachment` 의 domain 계층이 웹 타입을 참조한다**
`module/attachment/src/main/kotlin/com/langlez/attachment/domain/Attachment.kt:3-4` 가 `com.langlez.exception.LanglezException` 과 `org.springframework.http.HttpStatus` 를 import 한다. CLAUDE.md §1 은 "domain 에 웹/HTTP 타입은 넣지 않는다 — 불변식은 `require` 로 던지고 변환은 application 이 한다"라고 명시한다. 다른 8개 모듈의 domain 은 모두 준수한다.
- 수정 방향: `require { "메시지키" }` 로 바꾸고 `AttachmentService` 에서 `try/catch` 변환.

**E3. 사용되지 않는 코드**

| 대상 | 위치 | 상태 |
|---|---|---|
| echo 아웃박스 3종 | `module/echo/.../outbox/EchoOutBox.kt`, `EchoOutBoxHistory.kt`, `jpa/EchoOutBoxRepository.kt` | 저장하는 코드도 발행 스케줄러도 없다. 완전한 스캐폴딩 |
| `EchoRepository.aggregateDailyStats` | 정의 `module/echo/.../domain/EchoRepository.kt:42`, 구현 `.../infrastructure/EchoRepositoryImpl.kt:151` | 호출자 없음. `hashtag_daily_stat` 테이블이 영원히 비어 있다 |
| `Post.reportCount` | `module/echo/src/main/kotlin/com/langlez/echo/domain/Post.kt:31` | 증가시키는 코드 없음. 항상 0 |
| `member-created` / `member-handle-changed` 토픽 | `module/member/.../api/MemberEventListener.kt:17`, `:23` | 발행은 되나 저장소 전체에 컨슈머 없음(`@KafkaListener` 는 2개뿐) |

**E4. `@Scheduled` 인데 `@DistributedLock` 이 없는 메서드 1건 — 의도적으로 보임**
`infra/redis/src/main/kotlin/com/langlez/redis/cache/ResilientCacheProvider.kt:38` `checkRedisHealth()`. 인스턴스별 로컬 폴백 캐시 상태를 각자 판단해야 하므로 분산 락이 있으면 오히려 틀린다. 다만 CLAUDE.md §7 이 "`@Scheduled` 에는 `@DistributedLock` 을 반드시 함께 건다"고 단정하고 있어, 다음 사람이 "누락"으로 보고 붙일 위험이 있다.
- 수정 방향: 코드 아님. 왜 안 거는지 한 줄 주석 추가(CLAUDE.md §5 "일부러 안 걸었으면 이유를 주석으로" 규약).
- 나머지 6건(`ChatOutBoxScheduler:22`, `ChatReconciler:36`, `ChatMessagePublisher:37`, `MemberOnlineTracker:134`, `MemberOutBoxHistoryScheduler:18`, `MemberOutBoxScheduler:16`, `VisitCountSyncScheduler:15`)은 전부 `@DistributedLock` 병행. 위반 없음.

**E5. `infra/mongo` 가 빈 디렉터리다**
`infra/mongo/` 안에 `build/` 잔재만 있고 소스도 `build.gradle.kts` 도 없다. `settings.gradle.kts` 의 `includeModules("infra")` 는 `build.gradle.kts` 가 있는 디렉터리만 등록하므로 빌드에는 영향이 없다. 다만 `app/api/src/main/kotlin/com/langlez/MainApplication.kt:9` 주석이 "JPA/Mongo Repository 스캔은 infra/rdb, **infra/mongo** 의 커스텀 `@Enable*Repositories` 로 대체"라고 존재하지 않는 모듈을 가리킨다.
- 수정 방향: 디렉터리 삭제 + 주석 정정. Mongo 의존은 `module/chat/build.gradle.kts:20` 이 직접 들고 있다.

**E6. Swagger 문서 인터페이스 누락 3건**
CLAUDE.md §6 은 `{Domain}API` 분리를 "이 프로젝트의 핵심 관례"로 규정한다. 보유: `ChatAPI`, `MemberAPI`, `RelationshipAPI`, `WaveAPI`. 없음: `ProfileController`(7개 엔드포인트), `AuthController`(2개), `AttachmentController`(1개, 로컬 전용이라 면제 가능).

**E7. 와일드카드 import**
`module/profile/src/main/kotlin/com/langlez/profile/api/ProfileController.kt:8` `org.springframework.web.bind.annotation.*`, `:9` `java.util.Locale` 은 정상이나 `ProfileRequest.kt:6` `java.util.*`. CLAUDE.md §12 는 `jakarta.persistence.*` 외 와일드카드를 금지한다.

### F. 확인했으나 **결함 아님**

감사 항목으로 지시받았으나 실제 코드에서는 문제가 없었던 것들. 재조사 낭비를 막기 위해 남긴다.

| 점검 항목 | 결과 |
|---|---|
| **Flyway ↔ JPA 엔티티 불일치** | **없음.** `app/api/src/test/.../ProfileE2ETest.kt` 가 `MainApplication`(전 모듈 포함)을 Testcontainers Postgres 위에서 `ddl-auto: validate` + Flyway 로 기동하며, 재실행 시 통과했다. 전 엔티티가 마이그레이션과 일치한다는 실행 증거다 |
| fail-open 검증 (`x != null && x != y`) | 2건 발견, 둘 다 안전. `AuthService.kt:116` 은 `AuthController.kt:37` 이 `deviceId` 를 400 으로 선차단해 fail-open 경로가 막혀 있고 주석으로도 명시. `WebSecurityConfiguration.kt:65` 는 OAuth2 빈 선택적 구성이라 인가와 무관 |
| `@Retryable` vs `@EnableRetry` | 정상. `MemberService.kt:32` 의 `@Retryable` 에 대해 `module/member/.../config/MemberRetryConfiguration.kt:11` 이 `@EnableRetry` 를 선언 |
| `@Modifying` 쿼리 트랜잭션 | 정상. 3건(`PostJpaRepository.kt:16`, `:21`, `ChatRoomMemberJpaRepository.kt:16`) 모두 `clearAutomatically`/`flushAutomatically` 지정, 호출부가 `@Transactional` 서비스 메서드 |
| 읽고-쓰기 카운터 증가 | 없음. `likeCount`(`PostJpaRepository.kt:17,22`), `unreadCount`(`ChatRoomMemberJpaRepository.kt:17`) 는 DB 단일 UPDATE. `visitCount` 는 Redis 누적 후 `ProfileRepositoryImpl.kt:112` 에서 `visitCount.add(delta)` 단일 UPDATE |
| 클라이언트 URL 을 그대로 저장 | 없음. 첨부 확정은 전부 key 기반(`CloudAttachmentService.kt:50`, `LocalAttachmentService.kt:31`, `EchoPostCreateRequest.kt:14`, `ProfileRequest.ImageConfirm`). 프로필 대표 이미지 변경만 url 을 받는데(`ProfileController.kt:52`) `repo.findImageByUrl(memberId, fileUrl)` 로 소유자 스코프 조회라 안전 |
| 경로 이탈(path traversal) | 방어됨. `LocalAttachmentService.kt:61-66` 이 `canonicalPath` + `File.separator` 로 형제 경로까지 차단 |
| 엔티티 `data class` | 없음. `ProfileImage.kt:24` 의 `data class Key` 는 `@IdClass` 라 규약대로다 |
| `@Cacheable`/`CacheManager` 사용 | 운영 코드에 없음(테스트 클래스명 매칭 1건뿐) |
| cascade 있는 엔티티에 `deleteAllInBatch` | 없음. `MemberRepositoryImpl.kt:124` 에 왜 안 쓰는지 주석까지 있다 |
| 컨트롤러가 엔티티 반환 | 없음. 8개 컨트롤러 전부 응답 DTO 또는 `Storage.PresignedResult` |
| 목록 API 페이지 크기 상한 | 정상. chat/wave/relationship 모두 `size.coerceIn(1, MAX)` |
| `@MemberId` 미사용(본문으로 회원 id 수신) | 없음. 인증 필요한 모든 엔드포인트가 `@MemberId` 사용 |
| REST 리소스 소유자 검사(IDOR) | 정상. `NotificationService.kt:96`(알림 소유자), `ProfileService.kt:64,76`(이미지 소유자), `chatRepository.findParticipant`(방 참여자), `sessions.isParticipant`(wave) |

---

## 4. TODO / FIXME / `ponytail:` 주석 전수

### TODO (5건, FIXME 0건)

| 위치 | 내용 |
|---|---|
| `core/src/main/kotlin/com/langlez/core/TokenBlacklist.kt:3` | `TokenRevoker` 로 이름 변경, `remainingValiditySeconds` → `Duration` (PLAN.md 4.2-7) |
| `app/api/src/main/resources/application-production.yml:24` | 운영 DataSource 값 채우기 |
| `app/api/src/main/resources/application-production.yml:26` | `reWriteBatchedInserts=true` 포함 여부 확인 |
| `app/api/src/main/resources/application-production.yml:72` | 프론트엔드 운영 URL |
| `app/api/src/main/resources/application-production.yml:76` | CORS 허용 오리진 |
| `app/api/src/main/resources/application-production.yml:79` | S3/CloudFlare 설정 |

### `ponytail:` (의도적 단순화 6건 — 전부 상한과 승급 조건이 명시돼 있다)

| 위치 | 감수한 비용 |
|---|---|
| `module/echo/.../application/EchoService.kt:198` | 페이지의 작성자 수만큼 `isBlockedBetween` 호출 (페이지 상한 있음) |
| `module/echo/.../infrastructure/EchoRepositoryImpl.kt:122` | 같은 태그 동시 최초 사용 시 `UNQ_HASHTAG_NAME` 충돌로 글 작성 실패 가능 |
| `module/echo/.../infrastructure/EchoRepositoryImpl.kt:147` | `searchCount` 미갱신(조회 경로에 쓰기를 넣지 않으려고) |
| `module/chat/.../application/ChatService.kt:60` | 페이지 크기만큼 참여자 단건 조회 (PLAN.md 4.2-9) |
| `module/chat/.../infrastructure/mongo/ChatMessageRepositoryImpl.kt:62` | 활성 방 수만큼 Mongo 왕복 (PLAN.md 4.2-10) |
| `module/wave/.../api/WaveController.kt:42` | 방 하나당 Redis 왕복 1회 (목록 최대 50) |

---

## 5. 빌드 / 테스트 결과 (실제 실행)

Docker 사용 가능(`docker info` OK). Testcontainers 로 Postgres · Redis · Mongo · Kafka 기동.

**1차: `./gradlew build`** → `BUILD SUCCESSFUL in 1m 6s`, 그러나 `94 actionable tasks: 94 up-to-date` — **테스트가 실제로 돌지 않았다.** 이 결과는 검증으로 치지 않았다.

**2차: `./gradlew build --rerun-tasks`** → `BUILD FAILED in 6m 23s` (82 tasks executed)

```
com.langlez.profile.ProfileE2ETest > IllegalStateException FAILED
com.langlez.profile.ProfileE2ETest > executionError FAILED
> Task :app:api:test FAILED
```

근본 원인 (테스트 XML `Caused by` 체인):

```
Caused by: org.flywaydb.core.internal.exception.FlywaySqlException: Unable to obtain connection from database: The connection attempt failed.
Caused by: org.postgresql.util.PSQLException: The connection attempt failed.
Caused by: java.net.SocketTimeoutException: Read timed out
```

코드 결함이 아니라 Testcontainers Postgres 컨테이너 접속 타임아웃이다.

**3차: `./gradlew :app:api:test --rerun-tasks`** → `BUILD SUCCESSFUL in 1m 51s`. 동일 테스트가 그대로 통과. 2차 실패는 환경 플레이크로 확정.

### 최종 테스트 집계 (모든 XML 리포트 합산)

```
TOTAL: tests=360  failures=0  errors=0  skipped=0
```

| 모듈 | 테스트 수 |
|---|---|
| module/chat | 78 |
| module/member | 68 |
| module/profile | 35 |
| module/relationship | 32 |
| module/wave | 31 |
| module/auth | 30 |
| infra/redis | 17 |
| module/echo | 17 |
| module/attachment | 13 |
| module/notification | 13 |
| common | 13 |
| app/api | 9 |
| infra/kafka | 2 |
| infra/rdb | 2 |
| core | 0 (순수 인터페이스) |

Testcontainers 통합 테스트 보유: chat(4), member(1), notification(1), profile(1), relationship(1), app/api(1).
**미보유: echo, wave, attachment, auth.** echo·wave 는 자체 통합 테스트가 없지만 `app/api` E2E 가 전 모듈을 기동하므로 스키마 정합만은 검증된다.

---

## 6. 문서와 코드가 어긋난 지점

| # | 문서 | 실제 코드 | 판정 |
|---|---|---|---|
| 1 | `PLAN.md:117` — "마이그레이션 번호 배정: wave=V5, relationship=V6, echo=V7" | 마이그레이션은 `V1`~`V5` 뿐이고, wave(`wave_rooms`, `wave_messages`) · relationship(`member_follows`, `member_blocks`, `reports`, `relationship_event_outbox*`) · echo(`posts`, `comments`, `post_likes`, `post_hashtags`, `hashtags`, `hashtag_daily_stat`, `post_media`, `echo_event_outbox*`) 테이블이 **전부 `V1__init.sql` 안에** 있다. `V5` 는 `drop_wave_messages` 다 | **코드가 맞다.** V1 베이스라인을 재생성하면서 세 모듈 테이블을 흡수한 것으로 보인다. `PLAN.md:117` 은 계획 시점 기록이 갱신되지 않은 것. 실행 증거: `app/api` E2E 가 `ddl-auto: validate` 로 전 엔티티를 통과시킨다 |
| 2 | `CLAUDE.md:7` — "현황과 남은 작업은 `README.md` 를 본다" | 감사 시작 시점(커밋 `960cc4f`)의 `CLAUDE.md` 는 `PLAN.md`/`REVIEW.md` 를 가리켰고, 감사 도중 `README.md` 로 수정됐다. `README.md` 도 같은 시점에 새로 생겼다(둘 다 커밋되지 않은 작업 트리 변경) | 병행 작업 중. 이 감사의 §4/§6 은 커밋된 `PLAN.md` 기준이다. `PLAN.md` 와 새 `README.md` 중 어느 쪽이 정본인지 정리 필요 |
| 3 | `CLAUDE.md` 최초 판 — "확정된 결함 목록은 `REVIEW.md` 를 본다" | `REVIEW.md` 없음. `PLAN.md:9` 가 "구 `REVIEW.md` 는 전부 조치돼 삭제했다"고 설명 | `PLAN.md` 가 맞다 |
| 4 | `CLAUDE.md §1` — "모듈 간에는 서로를 직접 참조하지 않는다. `core` 의 포트와 이벤트를 거친다" | `module/auth`, `chat`, `profile`, `relationship`, `wave` 가 `build.gradle.kts` 에서 `implementation(project(":module:member"))` 를 직접 건다. `wave` 는 `:module:relationship` 도 직접 참조 | **코드가 관례를 벗어났다.** member 를 공용 기반 모듈로 쓰는 실용적 선택으로 보이나, 규약과 정면 충돌한다. 규약을 고치든(예: "member 는 예외") 참조를 걷어내든 한쪽으로 정리 필요 |
| 5 | `CLAUDE.md §7` — "`@Scheduled` 가 붙은 메서드에는 `@DistributedLock` 을 반드시 함께 건다" | `ResilientCacheProvider.kt:38` 은 의도적으로 걸지 않는다(§3-E4) | 코드가 옳고 규약이 예외를 못 담았다 |
| 6 | `CLAUDE.md §3` 예시 — `require(canChangeHandle(now)) { "member.handle.cooldown" }` 를 정상 사례로 제시 | 그 키가 i18n 번들 12개 전부에 없다(§3-D1) | 규약 예시 자체가 미등록 키를 쓰고 있다 |
| 7 | `MainApplication.kt:9` 주석 — "JPA/Mongo Repository 스캔은 infra/rdb, **infra/mongo** 의 커스텀 `@Enable*Repositories` 로 대체" | `infra/mongo` 는 소스가 0개인 빈 디렉터리(§3-E5) | 주석이 낡았다 |
| 8 | `PLAN.md:4.2-5` — "지금 컨슈머(`notification`, `relationship`)에 중복 수신 방어가 없어 … 신고가 두 건 쌓인다" | `RelationshipService.kt:99` 에 `existsReport` 선검사가 있다 | PLAN 이 현재 코드보다 비관적. 다만 유니크 제약이 없는 check-then-insert 라 동시성 방어는 여전히 불완전(§3-B1) |
| 9 | `PLAN.md:3` — "A. notification … B. relationship … C. echo … D. wave" 를 "재구축 완료"로 기술 | echo·notification 은 HTTP 진입점이 없고(§3-A1, A4), relationship 은 아웃박스가 발행되지 않는다(§3-A2) | "완료" 표기가 과하다. 서비스 계층까지는 완료, 노출/결선은 미완 |

---

## 7. 미확인 항목

정직하게 남긴다.

- **C1/C2 의 런타임 재현.** STOMP 클라이언트로 `/topic/**` 를 실제 구독해 타인 메시지가 흘러오는지 확인하지 않았다. 코드 경로와 Spring 심플 브로커의 `PathMatcher` 동작에 근거한 판정이다.
- **성능/부하 특성.** 아웃박스 폴링 주기(2초), Redis 왕복 횟수, Mongo 쿼리 플랜은 측정하지 않았다.
- **FCM 실전송.** `FcmPushSender` 는 자격증명 미설정 시 경고만 남기도록 돼 있어, 실제 전송 경로는 검증하지 않았다.
- **OAuth2 실연동.** Google/Apple 실제 토큰 교환은 검증하지 않았다.
- **Kafka DLT 동작.** 재시도 소진 후 DLT 라우팅을 실행으로 확인하지 않았다(테스트 2건뿐).
- **`docs/` 하위 문서와 코드의 정합성.** 감사 범위 밖으로 두었다.

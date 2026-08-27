# Langlez Backend 코드 감사 (AUDIT)

감사일: 2026-08-26 · 원 대상 커밋 `960cc4f`
**갱신일: 2026-08-28 · 갱신 기준 커밋 `324cff0`** (`960cc4f..324cff0` 구간, PR #3~#11 반영)
방식: 읽기 전용 정적 분석 + `./gradlew build --rerun-tasks` 실제 실행. 이 문서 외 파일은 수정하지 않았다.

**이번 갱신 방식:** PR 본문을 근거로 삼지 않았다. 각 결함이 가리키던 파일·라인을 다시 열어 코드로 확인한 뒤에만 "조치됨"으로 표시했다. 조치된 항목은 지우지 않고 상태만 바꿨다 — 지우면 왜 그렇게 됐는지 이력이 사라진다.

---

## 0. 이번 갱신 요약

| 구분 | 건수 | 내용 |
|---|---|---|
| 조치 확인 | 12건 | §3-A1~A4(치명 전부), §3-B1, §3-C1/C2, §3-D1, §3-E1, 신규 G1, G3, G4 |
| 부분 조치 | 1건 | §3-B2 — member·relationship 만. chat·echo 는 여전히 없다 |
| 미해결 확인 | 4건 | §3-B3, §3-B4, §3-B5, §3-E2/E5/E6/E7(변경 없음) |
| 오진 정정 | 1건 | §5 — `ProfileE2ETest` 실패 원인 |
| 신규 결함 추가 | 7건 | §3-G1~G7 (감사 당시 놓쳤던 것) |
| 완성도 | 약 80% → **약 88%** | §1 |

---

## 1. 전체 완성도: ~~약 80%~~ **약 88% (2026-08-28 갱신)**

### 산출 방식 (원 감사와 동일, 변경 없음)

모듈/컴포넌트별 완성도를 매기고, "이 프로젝트가 서비스로 성립하는 데 그 조각이 차지하는 비중"으로 가중 평균했다.

모듈 단위 점수는 아래 5개 축의 가중합이다.

| 축 | 가중치 | 판정 기준 |
|---|---|---|
| 4계층 구조(api/application/domain/infrastructure) | 10% | 디렉터리 존재 + 의존 방향 준수 |
| HTTP 엔드포인트 노출 | 30% | 서비스 로직이 실제로 외부에서 호출 가능한가 |
| 도메인·서비스 로직 완성도 | 25% | 유스케이스가 끝까지 이어지는가(이벤트 발행→소비 포함) |
| 테스트 | 20% | 단위 + Testcontainers 통합 존재 여부 |
| 인프라 결선(스키마·아웃박스·인가) | 15% | Flyway 정합, 아웃박스 발행, 인가 검사 |

컴포넌트 가중치(합 105 → 정규화, 원 감사와 동일):
member 12 · chat 14 · auth 10 · relationship 9 · echo 9 · profile 8 · notification 7 · wave 7 · attachment 5 · common 6 · app/api 6 · infra:rdb 4 · core 3 · infra:redis 3 · infra:kafka 2

### 갱신된 모듈 점수와 가중 합계

| 모듈 | 원 점수 | 갱신 점수 | 가중치 | 가중 기여 |
|---|---|---|---|---|
| member | 95% | 97% | 12 | 11.64 |
| chat | 88% | 90% | 14 | 12.60 |
| auth | 85% | 92% | 10 | 9.20 |
| relationship | 75% | 92% | 9 | 8.28 |
| echo | 45% | 70% | 9 | 6.30 |
| profile | 80% | 80% | 8 | 6.40 |
| notification | 55% | 85% | 7 | 5.95 |
| wave | 85% | 88% | 7 | 6.16 |
| attachment | 85% | 85% | 5 | 4.25 |
| common | 85% | 90% | 6 | 5.40 |
| app/api | 70% | 78% | 6 | 4.68 |
| infra/rdb | 90% | 93% | 4 | 3.72 |
| core | 95% | 96% | 3 | 2.88 |
| infra/redis | 95% | 96% | 3 | 2.88 |
| infra/kafka | 80% | 80% | 2 | 1.60 |

가중 합계 **91.94 / 105 = 87.6% → 약 88%**

각 점수 변경의 근거는 §2 표와 §3 각 항목에 있다. 다음 갱신 때 비교할 수 있도록 원 점수를 지우지 않고 남겼다.

### 한 줄 요약

원 감사가 지목한 치명 결함 4개(§3-A1~A4)는 전부 조치됐다 — echo·notification 이 HTTP 로 열렸고, relationship 아웃박스가 발행되며, 정지/탈퇴 회원의 잔여 토큰이 매 요청 차단된다. 여기에 원 감사가 놓쳤던 더 깊은 결함 하나가 같이 드러나 조치됐다: 아웃박스 발행이 relationship 만이 아니라 **member·chat 포함 전 모듈에서 통째로 멈춰 있었다**(§3-G1). 남은 것은 §3-B2(일부)·B3·B4·B5 와 echo/wave/attachment/auth 의 통합 테스트 부재, 그리고 아직 손대지 않은 `Member.verify()` 미배선(§3-G5).

---

## 2. 모듈별 완성도

| 모듈 | % | 근거 | 미비점 |
|---|---|---|---|
| `module/member` | 97% (↑95%) | 4계층 완비, 엔드포인트 9개, 소스 29 / 테스트 10, **테스트 79건**, 아웃박스 send + history 스케줄러 둘 다 존재 | ~~i18n 키 5개 누락~~ **조치됨 (PR #3, `641f888`)** · `MemberWithdrawnEvent` 부재(미해결) · `Member.verify()` 프로덕션 호출자 0건(§3-G5, 미해결) |
| `module/chat` | 90% (↑88%) | 엔드포인트 9개, 테스트 **83건**(WS 통합 4 · Mongo 통합 · reconciler 포함), 아웃박스 send 스케줄러 + `published` 플래그 발행기 | ~~WS 구독 인가 우회 경로~~ **조치됨 (PR #3, `641f888`, §3-C1)** · `ChatOutBoxHistoryScheduler` 없음(§3-B2, 미해결 — 부분 조치) |
| `module/auth` | 92% (↑85%) | OAuth2 성공 핸들러 + refresh/logout, 1인 1기기 바인딩, 테스트 30건, fail-open 방어 확인 | ~~정지/탈퇴 시 토큰 무효화 미구현~~ **조치됨 (PR #3, `641f888`, §3-A3)** · `infrastructure` 계층 없음(무해) · `AuthAPI` Swagger 인터페이스 없음(§3-E6, 미해결) |
| `module/wave` | 88% (↑85%) | 엔드포인트 7개, 테스트 31건, Redis 링버퍼 채팅, `wave_messages` 폐기(V5)와 코드 일치 | ~~WS 우회 경로~~ **조치됨 (PR #3, `641f888`, §3-C1)** · Testcontainers 통합 테스트 없음(미해결) |
| `module/attachment` | 85% (변경 없음) | presign→attach(key 기반) 흐름 완성, 경로 이탈 방어 있음, 테스트 13건 | `domain` 이 `HttpStatus`/`LanglezException` 참조(§3-E2, **재확인 — 변경 없음, 미해결**) |
| `module/profile` | 80% (변경 없음) | 엔드포인트 7개, 테스트 34건 + 동시성 통합 | `ProfileAPI` Swagger 없음(§3-E6) · **수정 요청에 검증 전무(§3-B4, 재확인 — 변경 없음, 미해결)** · 와일드카드 import(§3-E7) |
| `module/relationship` | 92% (↑75%) | 엔드포인트 **10개**(팔로워/팔로잉 목록 추가, PR #4), 테스트 **77건**(통합 5), `RelationshipAPI` 분리 | ~~아웃박스 발행 스케줄러 없음~~ **조치됨 (PR #5, `c6a7fdd`, §3-A2)** · ~~신고 중복이 check-then-insert 뿐~~ **조치됨 (PR #9, `aa6de8e`)** · 히스토리 아카이버 **조치됨 (PR #5)** · ~~이벤트 DTO 가 `core` 밖~~ **조치됨 (PR #5, §3-E1)** |
| `module/notification` | 85% (↑55%) | 컨슈머 + 3상태 판정 + FCM, 테스트 **32건**(통합 1) | ~~컨트롤러 없음~~ **조치됨 (PR #7, `6fb9b90`, §3-A4)** · ~~멱등성 없음~~ **조치됨 (PR #5, §3-B1)** · ~~구독 인가 없음~~ **조치됨 (PR #3, §3-C2)** · FCM 제목이 i18n 키 원문 노출(§3-G6, 미해결) |
| `module/echo` | 70% (↑45%) | 엔드포인트 **12개**(전부 노출), 테스트 **32건**, 좋아요 카운터는 DB 단일 UPDATE | ~~컨트롤러 없음~~ **조치됨 (PR #7, §3-A1)** · 아웃박스 이력 스케줄러 없음(§3-B2, 미해결) · 통합 테스트 없음(미해결) · `aggregateDailyStats` 호출자 없음 · `Post.reportCount` 미사용 |
| `core` | 96% (↑95%) | 포트 10종(`SubscriptionAuthorizer`, `MessageDeduplicator` 추가) + 이벤트 DTO 4종(relationship 추가) | `TokenBlacklist` 개명 TODO(변경 없음) · 회원 탈퇴 이벤트 부재(변경 없음) |
| `common` | 90% (↑85%) | 보안 설정 · JWT 필터(상태 검사 추가) · i18n **기본 번들 복구 + 85개 키**(12개 번들 동일) · `WebSocketSubscriptionGate` 신설, 테스트 28건 | `ExceptionResponse` 가 `status`+`message` 뿐(변경 없음) |
| `infra/rdb` | 93% (↑90%) | 아웃박스 베이스, Flyway, QueryDSL, **`OutBoxRepository.fetch` 트랜잭션 누락 조치(§3-G1)** | 히스토리 테이블 정리 배치 없음(§3-B3, 재확인 — 변경 없음, 미해결) |
| `infra/redis` | 96% (↑95%) | Resilient 캐시, 분산 락, pub/sub 브로드캐스터, **`RedisMessageDeduplicator` 신설**, 테스트 24건 | 헬스 스케줄러에 `@DistributedLock` 없음(의도적, §3-E4, 변경 없음) |
| `infra/kafka` | 80% (변경 없음) | 프로듀서/컨슈머 설정 + DLT, 테스트 2건 | 소스 1파일. 재시도·DLT 정책 테스트 얕음 |
| `app/api` | 78% (↑70%) | 전 모듈 조립, 테스트 16건(`ProfileE2ETest` + 신규 `MessageSourceAutoConfigurationTest`) | `application-production.yml` 이 TODO 플레이스홀더 상태(§3-B5, 재확인 — 변경 없음, 미해결) |
| `infra/mongo` | — (변경 없음) | **소스가 하나도 없다.** (§3-E5, 재확인 — 변경 없음) | 삭제 대상 |

---

## 3. 결함 목록

추측을 배제하고 코드로 확인된 것만 적었다. **조치된 항목은 지우지 않고 상태 줄을 추가했다.**

### A. 치명 — 전부 조치됨

**A1. `module/echo` 에 컨트롤러가 없다 — 모듈 전체가 HTTP로 도달 불가**

> **상태: 조치됨 (PR #7, 커밋 `6fb9b90`)** — `module/echo/src/main/kotlin/com/langlez/echo/api/EchoController.kt` 확인. `EchoAPI` Swagger 인터페이스와 함께 `/api/v1/echoes` 아래 12개 엔드포인트 전부 노출됨. `EchoControllerTest` 존재.

`module/echo/src/main/kotlin/com/langlez/echo/api/` 에는 `request/`·`response/` DTO 4개만 있고 `@RestController` 가 하나도 없다. 저장소 전체에서 `@RestController` 는 8개이며 echo 는 그중 없다.
- 증상: `EchoService` 의 12개 메서드(`createPost`, `homeTimeline`, `memberTimeline`, `hashtagTimeline`, `getPost`, `like`, `unlike`, `comment`, `listComments`, `deleteComment`, `deletePost`, `presignUpload`)가 전부 호출 경로 없음. 응답 DTO `EchoPostResponse`/`EchoCommentResponse` 도 미사용.
- 수정 방향: `EchoAPI`(Swagger 인터페이스) + `EchoController` 를 `/api/v1/echoes` 로 추가. `@MemberId` 로 회원 식별, 목록 엔드포인트에는 `size.coerceIn(1, MAX)` 상한.

**A2. `relationship` 아웃박스에 발행 스케줄러가 없다 — 이벤트가 영구 적재만 됨**

> **상태: 조치됨 (PR #5, 커밋 `c6a7fdd`)** — `module/relationship/src/main/kotlin/com/langlez/relationship/infrastructure/outbox/RelationshipOutBoxScheduler.kt`, `RelationshipOutBoxHistoryScheduler.kt` 둘 다 확인. `RelationshipOutBoxSchedulerTest` 존재. 단, 이 PR 에서 더 깊은 결함(§3-G1, `OutBoxRepository.fetch` 트랜잭션 누락)이 같이 드러났다 — relationship 만의 문제가 아니라 **member·chat 포함 전 모듈의 아웃박스 발행이 통째로 멈춰 있었다.**

`module/relationship/src/main/kotlin/com/langlez/relationship/api/RelationshipEventListener.kt:27` 이 `member-followed` 를 `RelationshipOutBox` 로 저장하지만, `module/relationship/.../infrastructure/outbox/` 에는 `RelationshipOutBox.kt`, `RelationshipOutBoxHistory.kt` 만 있고 `OutBoxProcessor` 를 상속한 스케줄러가 없다.
- 증상: 팔로우가 일어날 때마다 `relationship_event_outbox` 에 행이 쌓이고 Kafka 로는 한 건도 나가지 않는다.
- 수정 방향: `MemberOutBoxScheduler` 를 그대로 복사해 `RelationshipOutBoxScheduler` 추가.

**A3. 정지/탈퇴 회원의 액세스 토큰이 만료까지 그대로 살아 있다**

> **상태: 조치됨 (PR #3, 커밋 `641f888`)** — `common/src/main/kotlin/com/langlez/filter/JwtAuthenticationFilter.kt:90-91` 확인. `MemberStatusQuery` 로 매 요청 상태를 조회해 `SUSPENDED`/`WITHDRAWN` 을 403 `LanglezException` 으로 차단한다. `/api/v1/auth/` 는 면제(로그아웃·리프레시는 각자 `requireActive` 로 막힘). 회귀 테스트 `MemberStatusCacheRaceTest` 존재.

`common/src/main/kotlin/com/langlez/filter/JwtAuthenticationFilter.kt:45-62` 는 블랙리스트 · 토큰 타입 · 서명만 본다. `Member.status` 는 확인하지 않는다.
- 증상: 정지·탈퇴 직후에도 이미 발급된 액세스 토큰으로 모든 일반 API 를 계속 쓸 수 있다.
- 수정 방향: `JwtAuthenticationFilter` 뒤에 상태 검사 필터.

**A4. `module/notification` 에 컨트롤러가 없다 — 알림 조회/읽음 처리 도달 불가**

> **상태: 조치됨 (PR #7, 커밋 `6fb9b90`)** — `module/notification/src/main/kotlin/com/langlez/notification/api/NotificationController.kt`, `NotificationAPI.kt` 확인. `GET /api/v1/notifications`(커서 페이징, size 상한 50), `PATCH /api/v1/notifications/{id}/read` 존재. `NotificationControllerTest` 존재.

`module/notification/src/main/kotlin/com/langlez/notification/api/` 에 `NotificationConsumer.kt` 하나뿐이었다.
- 증상: `NotificationService.list`, `markRead` 가 호출 경로 없음.
- 수정 방향: `NotificationAPI` + `NotificationController`.

### B. 높음

**B1. Kafka 컨슈머에 멱등성 방어가 없다 (notification)**

> **상태: 조치됨 (PR #5, 커밋 `c6a7fdd`)** — `core/src/main/kotlin/com/langlez/core/MessageDeduplicator.kt` 신설, 구현체 `infra/redis/.../RedisMessageDeduplicator.kt`(SETNX 기반). `module/notification/src/main/kotlin/com/langlez/notification/api/NotificationConsumer.kt:45-54` 가 처리 전 `isDuplicate` 로 걸러 처리 후 실패 시 `release` 로 되돌린다. `module/relationship/.../RelationshipConsumer.kt` 도 같은 패턴. **단, 표시가 처리 성공 전에 남는 구조라 남은 한계가 있다 — §3-G7 참고.**

`module/notification/.../NotificationConsumer.kt` → `NotificationService.onChatMessage` → `notify()` 는 무조건 `repo.save(Notification(...))` 후 FCM 전송. 중복 검사가 없었다.
- 참고: relationship 쪽은 `RelationshipService.report` 의 `existsReport` 선검사로 부분 멱등이었는데, 이후 PR #9 에서 DB 유니크 제약까지 붙어 완전히 닫혔다(§3-B1 관련, PR #9).
- 수정 방향(적용됨): Redis SETNX 기반 `messageId`(페이로드 SHA-256) 중복 검사. 레디스 장애 시 fail-open.

**B2. `chat`·`echo`·`relationship` 아웃박스에 히스토리 아카이버가 없다**

> **상태: 부분 조치 (PR #5, 커밋 `c6a7fdd`)** — `RelationshipOutBoxHistoryScheduler` 신설 확인. **`ChatOutBoxHistoryScheduler`, `EchoOutBoxHistoryScheduler` 는 여전히 없다.** `module/chat`, `module/echo` 에 `find ... -iname "*OutBoxHistoryScheduler*"` 결과 없음(2026-08-28 재확인). `ChatOutBoxHistory`, `EchoOutBoxHistory` 엔티티는 있으나 채우는 쪽이 아직 없다.

`OutBoxHistoryProcessor` 를 상속한 클래스는 이제 `MemberOutBoxHistoryScheduler`, `RelationshipOutBoxHistoryScheduler` 둘이다.
- 증상: `chat_event_outbox`·`echo_event_outbox` 는 완료 행이 영원히 남는다.
- 수정 방향: `MemberOutBoxHistoryScheduler` 를 chat·echo 에도 복제.

**B3. 아웃박스 히스토리 테이블을 비우는 배치가 아예 없다**

> **상태: 미해결 (2026-08-28 재확인, 변경 없음)** — `infra/rdb/src/main/kotlin/com/langlez/rdb/outbox/` 하위 및 저장소 전체에 `*_outbox_history` 삭제/파티션 드롭 코드가 여전히 없다.

`infra/rdb/src/main/kotlin/com/langlez/rdb/outbox/OutBoxHistoryProcessor.kt:22-38` 은 완료 건을 `*_outbox_history` 로 옮기기만 한다.
- 증상: `member_outbox_history`, `relationship_outbox_history` 가 무한 증가(member·relationship 은 이제 채워지므로 이 문제가 실제로 발생하기 시작했다).
- 수정 방향: 보존 기간(예: 30일) 기준 `@Scheduled` + `@DistributedLock` 삭제 배치.

**B4. 프로필 수정 요청에 검증이 전혀 걸려 있지 않다**

> **상태: 미해결 (2026-08-28 재확인, 변경 없음)** — `module/profile/src/main/kotlin/com/langlez/profile/api/ProfileRequest.kt:15-24` 의 `Update` 에 Bean Validation 어노테이션 여전히 없음. `ProfileController.kt:22` 의 `@RequestBody request: ProfileRequest.Update` 에 `@Valid` 여전히 없음.

`bio`/`goal`/`want` 길이 제한이 없다. i18n 에 `validation.member.bio.size` 등이 준비돼 있는데 참조하는 코드가 여전히 없다.
- 수정 방향: `Update` 에 `@field:Size(max = ..., message = "validation.member.bio.size")` 등을 붙이고 컨트롤러에 `@Valid` 추가.

**B5. `application-production.yml` 이 플레이스홀더 상태다**

> **상태: 미해결 (2026-08-28 재확인, 변경 없음)** — `app/api/src/main/resources/application-production.yml:24,26,72,76,79` TODO 5건 그대로.

증상: production 프로필로는 기동 불가 또는 오설정 기동.
- 수정 방향: 환경변수 주입으로 전환.

### C. 중간 (인가) — 전부 조치됨

**C1. WebSocket 구독 인가가 접두사에 걸리지 않는 목적지에 대해 열려 있다**

> **상태: 조치됨 (PR #3, 커밋 `641f888`)** — `common/src/main/kotlin/com/langlez/config/WebSocketSubscriptionGate.kt` 신설 확인. `core.SubscriptionAuthorizer` 인터페이스 도입, `chat`/`wave`/`notification` 각각 `{Domain}SubscriptionAuthorizer` 구현체 보유(`ChatSubscriptionAuthorizer`, `WaveSubscriptionAuthorizer`, `NotificationSubscriptionAuthorizer`). 게이트가 모든 SUBSCRIBE 를 받아 `supports` 가 참인 authorizer 가 하나도 없으면 **거부**로 뒤집혔다. `WaveWebSocketConfiguration` 의 자체 인터셉터는 제거됨.

기존에는 `chat`·`wave` 인터셉터가 각자 자기 접두사가 아니면 통과시켜, `/topic/**` 처럼 접두사에 안 걸리는 구독이 전체 방을 흡입할 수 있었다.
- 확인 수준: 코드 경로로 확인. 런타임 STOMP 재현은 원 감사와 동일하게 **미확인** — 이번 갱신에서도 하지 않았다.
- 수정 방향(적용됨): 화이트리스트 정규식 기반 기본 거부 게이트.

**C2. 알림 토픽 구독에 인가 검사가 없다**

> **상태: 조치됨 (PR #3, 커밋 `641f888`)** — C1 과 같은 게이트로 해소. `NotificationSubscriptionAuthorizer` 가 `/topic/notification/{memberId}` 를 소유자 일치로 판정한다.

- 확인 수준: 코드 경로로 확인. 런타임 재현은 **미확인**.

### D. 중간 (i18n) — 조치됨, 원 진단보다 훨씬 심각했다

**D1. i18n 키 5개가 12개 번들 전부에 없다**

> **상태: 조치됨 (PR #3, 커밋 `641f888`)** — 5개 키(`member.handle.cooldown`, `member.handle.invalid`, `member.handle.duplicated`, `member.already-verified`, `member.already-withdrawn`) 전부 12개 번들에 확인됨(`messages_ko.properties:25-29` 등). **하지만 이 5개 키 누락은 진짜 문제의 일부였을 뿐이다 — §5 정정 참고. 로케일 접미사 없는 기본 번들(`common/src/main/resources/messages.properties`) 자체가 없어서 `MessageSourceAutoConfiguration` 이 통째로 안 켜져 있었고, 그 결과 12개 번들의 77개 키 전부가 사용자 응답에서 조회 실패였다.** 지금은 기본 번들이 복구됐고(주석 전용, 회귀 방지 목적) 키 수는 12개 번들 전부 **85개**로 동일하다.

- 참고: 원래 있던 `member.init.handle.*` 접두사 이슈는 **통일되지 않았다.** `common/src/main/resources/messages_ko.properties:22-23` 에 `member.init.handle.invalid`/`.duplicated` 가 그대로 남아 있고, 코드가 실제로 던지는 건 `:25-27` 의 `member.handle.*`(접두사 없음)다. `member.init.handle.*` 는 참조하는 코드가 없는 죽은 키다(§3-E3 급의 사소한 정리 대상, 응답에 노출되진 않으니 기능 결함은 아니다).
- 수정 방향(적용됨): 기본 번들 파일 복구 + 5개 키 추가. 회귀 방지 `MessageSourceAutoConfigurationTest` 신설.

### E. 낮음 (규약 위반 · 정리 대상)

**E1. 도메인 이벤트 DTO 가 `core` 밖에 있다**

> **상태: 조치됨 (PR #5, 커밋 `c6a7fdd`)** — `MemberFollowedEvent` 가 `core/src/main/kotlin/com/langlez/core/event/relationship/` 로 이동 확인. `followId` 필드도 추가돼 컨슈머 멱등 키의 근거가 됐다(§3-B1).

**E2. `attachment` 의 domain 계층이 웹 타입을 참조한다**

> **상태: 미해결 (2026-08-28 재확인, 변경 없음)** — `module/attachment/src/main/kotlin/com/langlez/attachment/domain/Attachment.kt:3-4` 에 `com.langlez.exception.LanglezException`, `org.springframework.http.HttpStatus` import 그대로 있음. 이번 갱신 구간 PR 어느 것도 attachment 를 건드리지 않았다.

- 수정 방향: `require { "메시지키" }` 로 바꾸고 `AttachmentService` 에서 `try/catch` 변환.

**E3. 사용되지 않는 코드**

> **상태: 재확인 — echo 아웃박스 스캐폴딩은 여전히 미사용(아웃박스 자체를 안 쓰는 설계로 보임, echo 는 A1 조치로 컨트롤러는 생겼지만 발행 경로는 그대로 스캐폴딩이다). `member-created`/`member-handle-changed` 컨슈머 부재도 변경 없음.**

| 대상 | 위치 | 상태 |
|---|---|---|
| echo 아웃박스 3종 | `module/echo/.../outbox/EchoOutBox.kt`, `EchoOutBoxHistory.kt`, `jpa/EchoOutBoxRepository.kt` | 저장 코드도 발행 스케줄러도 없음. 완전한 스캐폴딩 (미해결) |
| `EchoRepository.aggregateDailyStats` | `module/echo/.../domain/EchoRepository.kt:42`, `.../infrastructure/EchoRepositoryImpl.kt:151` | 호출자 없음 (미해결) |
| `Post.reportCount` | `module/echo/src/main/kotlin/com/langlez/echo/domain/Post.kt:31` | 증가시키는 코드 없음, 항상 0 (미해결) |
| `member-created` / `member-handle-changed` 토픽 | `module/member/.../api/MemberEventListener.kt` | 발행은 되나 컨슈머 없음 (미해결) |

**E4. `@Scheduled` 인데 `@DistributedLock` 이 없는 메서드 1건 — 의도적으로 보임**

> **상태: 재확인, 변경 없음.** `infra/redis/.../ResilientCacheProvider.kt:38` `checkRedisHealth()` 그대로.

**E5. `infra/mongo` 가 빈 디렉터리다**

> **상태: 재확인, 변경 없음.** `infra/mongo/` 여전히 소스 없음.

**E6. Swagger 문서 인터페이스 누락**

> **상태: 부분 개선.** echo·notification 은 A1/A4 조치로 `EchoAPI`·`NotificationAPI` 가 새로 생겨 보유 목록에 추가됐다. **`ProfileController`(7개 엔드포인트), `AuthController`(2개) 는 여전히 없음.** `AttachmentController`(1개, 로컬 전용)는 원 감사대로 면제 가능.
> 보유: `ChatAPI`, `MemberAPI`, `RelationshipAPI`, `WaveAPI`, **`EchoAPI`, `NotificationAPI`**(신규). 없음: `ProfileAPI`, `AuthAPI`.

**E7. 와일드카드 import**

> **상태: 재확인, 변경 없음.** `ProfileRequest.kt:6` `java.util.*` 그대로.

### F. 확인했으나 **결함 아님** (원 감사 그대로, 변경 없음)

감사 항목으로 지시받았으나 실제 코드에서는 문제가 없었던 것들. 재조사 낭비를 막기 위해 남긴다. 이번 갱신에서 다시 검증하지 않았다 — 해당 코드 영역이 이번 PR 구간(§3 A~E, G)에서 건드려지지 않았기 때문이다.

| 점검 항목 | 결과 |
|---|---|
| Flyway ↔ JPA 엔티티 불일치 | 없음. `ProfileE2ETest` 가 Testcontainers Postgres 위에서 `ddl-auto: validate` + Flyway 로 기동, 통과 |
| fail-open 검증 (`x != null && x != y`) | 2건 발견, 둘 다 안전 |
| `@Retryable` vs `@EnableRetry` | 정상 |
| `@Modifying` 쿼리 트랜잭션 | 정상 |
| 읽고-쓰기 카운터 증가 | 없음. DB 단일 UPDATE |
| 클라이언트 URL 을 그대로 저장 | 없음. key 기반 |
| 경로 이탈(path traversal) | 방어됨 |
| 엔티티 `data class` | 없음 |
| `@Cacheable`/`CacheManager` 사용 | 운영 코드에 없음 |
| cascade 있는 엔티티에 `deleteAllInBatch` | 없음 |
| 컨트롤러가 엔티티 반환 | 없음 |
| 목록 API 페이지 크기 상한 | 정상 |
| `@MemberId` 미사용 | 없음 |
| REST 리소스 소유자 검사(IDOR) | 정상 |

### G. 원 감사가 놓친 결함 — 이번 갱신에서 추가

작업 중 발견됐으나 원 감사(§3 A~F)에 없던 것들. 전부 코드로 확인했다.

**G1. `OutBoxRepository.fetch` 에 트랜잭션이 없어 아웃박스 발행이 전 모듈에서 통째로 멈춰 있었다**

> **상태: 조치됨 (PR #5, 커밋 `c6a7fdd`)** — `infra/rdb/src/main/kotlin/com/langlez/rdb/outbox/OutBoxRepository.kt:34` 에 `@Transactional` 확인, KDoc(`:22-33`)에 이유가 명시돼 있다.

`@Lock(PESSIMISTIC_WRITE)` 가 걸린 `fetch` 쿼리는 파생 쿼리라 Spring Data 의 기본 트랜잭션(`SimpleJpaRepository` 의 CRUD 메서드에만 붙는다)을 못 받는다. 트랜잭션 없이 잠금 쿼리를 쏘면 하이버네이트가 매번 `Query requires transaction be in progress` 로 터진다.
- **증상: `relationship` 만이 아니라 `member`·`chat` 을 포함해 `OutBoxProcessor` 를 상속하는 아웃박스 전부가 이 경로를 탄다. 원 감사 §3-A2 는 "relationship 만 스케줄러가 없다"고 봤지만, 실제로는 스케줄러가 있어도(member, chat) 발행이 안 되고 있었다.** 기존 `OutBoxProcessorTest` 가 `repo.fetch` 를 목으로 대체해 이 결함을 가리고 있었고, PR #5 에서 relationship 아웃박스 **통합** 테스트(실제 Testcontainers Postgres)를 처음 넣으면서 드러났다.
- 수정 방향(적용됨): `OutBoxRepository.fetch` 파생 쿼리에 `@Transactional` 명시.

**G2. 전역 i18n 장애 — 5개 키 누락이 아니라 77개 키 전부가 조회 실패였다**

> **상태: 조치됨 (PR #3, 커밋 `641f888`).** 세부 내용은 §3-D1 과 §5 참고. 원 감사가 "5개 키 누락"으로 좁게 진단한 것을, 실제 재현 시 "로케일 접미사 없는 기본 번들 부재로 `messageSource` 빈 자체가 안 뜬다"로 정정한다.

**G3. `member_follows`·`member_blocks` 인덱스 부재로 목록 조회가 full scan / PK 역순 훑기였다**

> **상태: 조치됨 (PR #4, 커밋 `440d698`)** — `infra/rdb/src/main/resources/migration/V6__follow_lookup_indexes.sql` 확인. `IDX_MEMBER_FOLLOW_FOLLOWED`, `IDX_MEMBER_FOLLOW_FOLLOWER`, `IDX_MEMBER_BLOCK_BLOCKER` 3개 인덱스 추가. `FollowIndexIntegrationTest` 가 EXPLAIN 으로 실제 계획을 고정.

`member_follows` 에 있던 인덱스는 `UNQ_MEMBER_FOLLOW(follower_id, followed_id)` 뿐이라 `followed_id` 단독 조회(팔로워 목록)가 full scan 이었다. `follower_id` 방향도 유니크 인덱스에 `id` 가 없어 정렬을 못 주는 탓에 플래너가 PK 역순 훑기로 빠졌다. `member_blocks` 의 차단 목록 조회도 같은 문제였다.

**G4. `RelationshipConsumer` 의 역직렬화가 `try` 밖에 있어 깨진 페이로드가 오면 신고가 DLT 에도 못 가고 사라졌다**

> **상태: 조치됨 (PR #5 리뷰에서 발견, 같은 PR 의 커밋 `fda6213` 에서 조치)** — `module/relationship/src/main/kotlin/com/langlez/relationship/api/RelationshipConsumer.kt:37-51` 확인. `mapper.readValue` 가 `try` 블록 **안**에 있고, 실패 시 `dedup.release` 후 예외를 다시 던진다.

원래 역직렬화가 `dedup.isDuplicate` 통과 직후·`try` 진입 전에 있었다면, 깨진 페이로드는 중복 표시만 남긴 채 처리되지 않고 예외가 그대로 나가 재시도·DLT 재투입이 전부 "중복"으로 걸러졌을 것이다. 현재 코드는 이미 이 문제를 피해간 형태로 존재한다.

**G5. `Member.verify()` 프로덕션 호출자가 0건이다 — 상태 머신이 절반만 배선됐다**

> **상태: 미해결.** `common/src/main/kotlin/com/langlez/filter/JwtAuthenticationFilter.kt:80` 주석이 "`Member.verify()` 를 호출하는 엔드포인트가 아직 없다. 여기서 막으면 신규 가입자가 전부 잠긴다"고 명시. `module/member/.../application/MemberService.kt:74` 의 `verify(id: Long)` 래퍼도 호출부가 테스트 밖에는 없다(`grep -rn "\.verify(" module app common --include="*.kt"` 로 재확인, 테스트 제외 결과 0건).

전 회원이 영구 `Member.Status.CREATED` 상태다. `Status.ACTIVE` 로 전이시키는 경로가 없어 `CREATED`/`ACTIVE` 구분이 실질적으로 무의미하다. PR #3 이 `CREATED` 를 §3-A3 의 정지/탈퇴 차단 대상에서 뺀 이유이기도 하다(막으면 신규 가입자 전원이 잠긴다).
- 수정 방향: 이메일/휴대폰 인증 플로우를 만들거나, 당장 안 만들 거면 `Status.CREATED`/`ACTIVE` 구분 자체를 걷어내는 쪽이 더 정직하다.

**G6. FCM 푸시 제목이 i18n 키 원문으로 OS 배너에 그대로 렌더된다**

> **상태: 미해결 (호출부까지 확인 완료 — 확정).** `module/notification/src/main/kotlin/com/langlez/notification/application/NotificationService.kt:82` `title = TITLE_CHAT_MESSAGE`, `:98` `title = TITLE_MEMBER_FOLLOWED` — 둘 다 상수로 정의된 **i18n 메시지 키**다. 주석(`:80-81`)이 의도를 명시한다: "제목도 같은 방식으로 메시지 키를 넘겨 클라이언트가 사용자 언어로 그린다." 이 설계는 **인앱 브로드캐스트 경로**(`broadcaster.broadcast`, `NotificationService.kt:57`)에는 맞다 — 클라이언트가 키를 받아 직접 번역한다. 하지만 같은 `title` 값이 `NotificationService.kt:66` `push.send(token, title, body, data)` 를 거쳐 `FcmPushSender.kt:44` `.setNotification(FcmNotification.builder().setTitle(title)...)` 로 그대로 들어간다. `.setNotification(...)` 으로 만든 FCM 메시지는 **OS 가 앱 코드 개입 없이 배너를 직접 그린다** — 클라이언트가 키를 번역할 기회 자체가 없다.
> - 증상: 채팅 메시지·팔로우 푸시 알림의 OS 배너 제목이 `chat.notification.title` 같은 키 문자열 그대로 뜬다.
> - 수정 방향: FCM 경로는 서버가 회원 로케일 기준으로 `messageSource.getMessage(key, ..., locale)` 번역 후 `setTitle` 에 넘기거나, `.setNotification()` 대신 data-only 메시지로 바꿔 클라이언트가 키를 받아 직접 로컬 알림을 그리게 한다(인앱 경로와 같은 방식으로 통일).

**G7. `MessageDeduplicator` 표시가 처리 성공 전에 남아, 강제 종료 시 유실될 수 있다 — 문서화된 한계**

> **상태: 미해결 (설계상 감수한 한계로 KDoc 에 명시돼 있음).** `core/src/main/kotlin/com/langlez/core/MessageDeduplicator.kt:31-38` 이 한계를 직접 서술한다: "`Exception` 이 잡혔을 때만 되돌림이 돈다. `Error`(OOM 등)나 프로세스 강제 종료(SIGKILL, OOMKilled)로 죽으면 표시만 남고 오프셋은 커밋되지 않는다. 재기동 후 카프카가 다시 흘려도 '중복'으로 걸러져 그대로 유실된다 — TTL 이 풀릴 때까지."

이 창을 닫으려면 표시를 **처리 성공 후**로 옮겨야 하는데(전형적 idempotent-consumer 패턴), 그러면 리밸런싱 중 겹치는 재배달을 못 막게 되는 트레이드오프가 있다. 결함이라기보다 설계 결정이지만, 다음 사람이 "왜 이렇게 짰지"를 다시 조사하지 않도록 남긴다.
- 수정 방향(필요 시): 처리 성공 후 표시로 전환 + 리밸런싱 재배달은 `RelationshipConsumer` 처럼 서비스 계층의 존재 검사(§3-B1 참고)로 이중 방어.

---

## 4. TODO / FIXME / `ponytail:` 주석 전수 (2026-08-28 재확인)

### TODO (6건, FIXME 0건) — 원 감사 대비 변경 없음

| 위치 | 내용 |
|---|---|
| `core/src/main/kotlin/com/langlez/core/TokenBlacklist.kt:3` | `TokenRevoker` 로 이름 변경, `remainingValiditySeconds` → `Duration` |
| `app/api/src/main/resources/application-production.yml:24` | 운영 DataSource 값 채우기 |
| `app/api/src/main/resources/application-production.yml:26` | `reWriteBatchedInserts=true` 포함 여부 확인 |
| `app/api/src/main/resources/application-production.yml:72` | 프론트엔드 운영 URL |
| `app/api/src/main/resources/application-production.yml:76` | CORS 허용 오리진 |
| `app/api/src/main/resources/application-production.yml:79` | S3/CloudFlare 설정 |

### `ponytail:` (의도적 단순화 6건 — 원 감사 대비 변경 없음)

| 위치 | 감수한 비용 |
|---|---|
| `module/echo/.../application/EchoService.kt:198` | 페이지의 작성자 수만큼 `isBlockedBetween` 호출 |
| `module/echo/.../infrastructure/EchoRepositoryImpl.kt:122` | 같은 태그 동시 최초 사용 시 `UNQ_HASHTAG_NAME` 충돌로 글 작성 실패 가능 |
| `module/echo/.../infrastructure/EchoRepositoryImpl.kt:147` | `searchCount` 미갱신 |
| `module/chat/.../application/ChatService.kt:60` | 페이지 크기만큼 참여자 단건 조회 |
| `module/chat/.../infrastructure/mongo/ChatMessageRepositoryImpl.kt:62` | 활성 방 수만큼 Mongo 왕복 |
| `module/wave/.../api/WaveController.kt:42` | 방 하나당 Redis 왕복 1회 |

---

## 5. 빌드 / 테스트 결과 (실제 실행, 2026-08-28 갱신)

Docker 사용 가능. Testcontainers 로 Postgres · Redis · Mongo · Kafka 기동.

**1차: `./gradlew build --rerun-tasks`** → `BUILD FAILED in 8m 45s` (93 tasks executed)

```
com.langlez.profile.infrastructure.ProfileRepositoryImplTest > SpecInstantiationException FAILED
    org.redisson.client.RedisConnectionException
        org.redisson.client.RedisTimeoutException
34 tests completed, 2 failed
```

이 실패로 Gradle 이 스케줄되지 않은 후속 태스크(`:module:relationship:test` 등)를 시작하지 않고 빌드를 중단했다 — 실패 자체와는 무관한 부수 효과다.

**2차: `./gradlew :module:profile:test --rerun-tasks`** → `BUILD SUCCESSFUL in 27s`. 동일 테스트 재실행, 그대로 통과.

**3차: `./gradlew :module:relationship:test --rerun-tasks`** → `BUILD SUCCESSFUL in 32s`. 1차 빌드가 중단되며 못 돌았던 테스트를 별도 실행, 통과.

**결론: 코드 결함이 아니라 Testcontainers Redis 컨테이너 접속 타임아웃(환경 플레이크)이 다시 발생했다.** 원 감사와 같은 종류(그때는 Postgres, 이번엔 Redis)지만 재현 메커니즘은 같다 — Testcontainers 컨테이너 기동/접속이 시스템 부하에 따라 간헐적으로 타임아웃난다.

### §5 정정 — 원 감사의 `ProfileE2ETest` 실패 원인 결론은 틀렸다

원 감사(2026-08-26)는 아래처럼 결론냈다:

> 근본 원인 (테스트 XML `Caused by` 체인): `FlywaySqlException: Unable to obtain connection` ← `PSQLException` ← `SocketTimeoutException`. 코드 결함이 아니라 Testcontainers Postgres 컨테이너 접속 타임아웃이다. 3차 재실행이 통과해 환경 플레이크로 확정.

**이 결론은 절반만 맞았다.** 실제로는 두 가지가 섞여 있었다:

1. Testcontainers Postgres 접속 타임아웃 — 이건 원 감사 말대로 진짜 환경 플레이크였다.
2. **`messages.properties`(로케일 접미사 없는 기본 번들) 부재로 `MessageSourceAutoConfiguration` 이 아예 안 켜져, `messageSource` 빈이 `DelegatingMessageSource` 로 대체되면서 모든 i18n 키 조회가 실패하고 있었다.** `GlobalRestControllerAdvice` 가 이 상태에서 키를 못 찾으면 키 문자열을 그대로 응답에 담는다. `ProfileE2ETest` 가 응답 메시지 내용을 단언하는 부분이 있었다면(예: 특정 에러 메시지 문자열 검사), **그 메시지 단언이 실패하고 있었을 것이다** — 이건 환경 문제가 아니라 코드 결함(§3-D1/G2)이었다.

**왜 원 감사가 이렇게 오판했는가:** 재실행하면 Postgres 타임아웃은 사라지고(진짜 플레이크라서) 통과했다. 하지만 i18n 결함은 **커밋된 코드에 상시 존재**하는 결함이라 재실행 여부와 무관하게 항상 같은 증상을 냈어야 한다. 원 감사가 본 실패 로그의 `Caused by` 체인 최상단이 `FlywaySqlException`(Postgres 접속 실패)이었다는 것은, **그 특정 실행에서는 애플리케이션 컨텍스트 기동 자체가 Postgres 접속 단계에서 멈춰서 i18n 문제가 드러날 지점(응답 메시지 단언)까지 아예 도달하지 못했다**는 뜻이다. 즉 두 문제가 같은 테스트 클래스에 동시에 존재했는데, 마침 그 실행에서는 앞단의 인프라 플레이크가 뒷단의 코드 결함을 가렸다. 재실행에서 Postgres 가 정상 접속되자 컨텍스트가 끝까지 떴고, i18n 결함이 있었다면 그때 드러났어야 하는데 원 감사는 "3차 재실행 통과 = 플레이크 확정"으로 결론짓고 응답 본문 내용까지는 재검토하지 않은 것으로 보인다.

PR #3(`641f888`)에서 기본 번들을 복구하고 `MessageSourceAutoConfigurationTest` 를 회귀 방지로 추가한 것으로 이 결함은 조치됐다(§3-D1/G2).

### 최종 테스트 집계 (모든 XML 리포트 합산, 2026-08-28)

```
TOTAL: tests=485  failures=0  errors=0  skipped=0
```
(원 감사 대비 360 → 485, +125건. profile 모듈은 Redis 플레이크로 실패했던 것을 재실행 결과로 대체 집계, relationship 은 1차 빌드에서 스케줄되지 않아 별도 실행분으로 집계)

| 모듈 | 테스트 수 (원 감사) | 테스트 수 (갱신) |
|---|---|---|
| module/chat | 78 | **83** |
| module/relationship | 32 | **77** |
| module/member | 68 | **79** |
| module/profile | 35 | **36** |
| module/notification | 13 | **32** |
| module/echo | 17 | **32** |
| module/wave | 31 | 31 |
| module/auth | 30 | 30 |
| infra/redis | 17 | **24** |
| module/attachment | 13 | 13 |
| common | 13 | **28** |
| app/api | 9 | **16** |
| infra/kafka | 2 | 2 |
| infra/rdb | 2 | 2 |
| core | 0 | 0 (순수 인터페이스) |

Testcontainers 통합 테스트 보유 (원 감사 → 갱신): chat(4→4), member(1→2), notification(1→1), profile(1→1), **relationship(1→5)**.
**미보유, 변경 없음: echo, wave, attachment, auth.**

---

## 6. 문서와 코드가 어긋난 지점

원 감사 표를 그대로 두고, 이번 갱신 구간에서 상태가 바뀐 항목만 갱신 줄을 추가했다.

| # | 문서 | 실제 코드 | 판정 |
|---|---|---|---|
| 1 | `PLAN.md:117` — 마이그레이션 번호 배정 | V1 베이스라인에 세 모듈 테이블 흡수 | 코드가 맞다. (갱신 참고: 지금은 `V6`(팔로우 인덱스, PR #4), `V7`(신고 유니크, PR #9)까지 있다 — 마이그레이션은 `V1`~`V7`) |
| 2 | `CLAUDE.md:7` — 현황은 `README.md` | 원 감사 시점 병행 작업 중 | 이번 갱신 시점엔 `README.md` 가 정착돼 있고 §0 의 "현재 도메인 모듈" 목록도 최신과 일치함을 확인 |
| 3 | `CLAUDE.md` 최초 판 — `REVIEW.md` | 없음, `PLAN.md` 가 흡수 | `PLAN.md` 가 맞다 (변경 없음) |
| 4 | `CLAUDE.md §1` — "모듈 간에는 서로를 직접 참조하지 않는다" | `auth`, `chat`, `profile`, `relationship`, `wave` 가 `:module:member` 직접 참조, `wave` 는 `:module:relationship` 도 | **재확인, 변경 없음.** 여전히 규약과 정면 충돌 중. 이번 갱신 구간 PR 어느 것도 이 참조 구조를 정리하지 않았다 |
| 5 | `CLAUDE.md §7` — `@Scheduled` 에 `@DistributedLock` 필수 | `ResilientCacheProvider.kt:38` 예외 | 코드가 옳고 규약이 예외를 못 담음 (변경 없음) |
| 6 | `CLAUDE.md §3` 예시 — `member.handle.cooldown` 키 사용 | 원 감사 시점엔 그 키가 12개 번들 전부에 없었음 | **조치됨 (PR #3).** 지금은 키가 존재해 규약 예시와 코드가 일치한다 |
| 7 | `MainApplication.kt:9` 주석 — `infra/mongo` 언급 | `infra/mongo` 는 빈 디렉터리 | 주석이 낡음 (변경 없음, 재확인) |
| 8 | `PLAN.md:4.2-5` — notification·relationship 컨슈머 중복 방어 없음 | 원 감사 시점엔 relationship 만 부분 방어 | **조치됨 (PR #5, #9).** 지금은 둘 다 `MessageDeduplicator` + (relationship 은 추가로 DB 유니크 제약)로 방어된다 |
| 9 | `README.md`(당시) — A.notification/B.relationship/C.echo/D.wave "재구축 완료" | echo·notification 은 HTTP 진입점 없음, relationship 은 아웃박스 미발행 | **이번 갱신 시점엔 실제로 "완료"에 가깝다.** §3-A1~A4 전부 조치돼 서비스 계층뿐 아니라 노출·결선까지 끝났다. 남은 건 §3-B2(일부)·B3·B4 같은 부수 기능이지 노출 자체는 아니다 |

---

## 7. 미확인 항목

정직하게 남긴다. 원 감사의 미확인 목록 중 이번 갱신에서 새로 확인한 것은 없다 — 문서 작업 범위라 런타임 재현·부하 테스트는 하지 않았다. 이번 갱신에서 새로 발견된 미확인 항목만 추가한다.

- **C1/C2 의 런타임 재현.** (원 감사와 동일, 미확인 유지) STOMP 클라이언트로 `/topic/**` 를 실제 구독해 게이트가 막는지 확인하지 않았다.
- **성능/부하 특성.** (원 감사와 동일, 미확인 유지)
- **FCM 실전송.** (원 감사와 동일, 미확인 유지)
- **OAuth2 실연동.** (원 감사와 동일, 미확인 유지)
- **Kafka DLT 동작.** (원 감사와 동일, 미확인 유지)
- **`docs/` 하위 문서와 코드의 정합성.** (원 감사와 동일, 범위 밖)
- (해소) `NotificationService.notify` 호출부의 `title` 출처는 §3-G6 에서 확인 완료 — 미확인 항목에서 제외.
- (해소, 결과는 미조치) `member.init.handle.*` 와 `member.handle.*` 키 중복은 확인했다 — **PR #3 이후에도 통일되지 않았다.** `common/src/main/resources/messages_ko.properties:22-23` 에 `member.init.handle.invalid`/`.duplicated` 가 여전히 남아 있고, `Member.kt`/`MemberService.kt` 가 던지는 키는 `:25-27` 의 `member.handle.*`(접두사 없음)다. 두 세트가 공존하며 `member.init.handle.*` 쪽은 참조하는 코드가 없는 죽은 키로 보인다(§3-E3 성격의 사소한 정리 대상, 기능 결함은 아님).

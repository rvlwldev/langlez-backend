# Remaining Modules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: superpowers:test-driven-development. 각 태스크는 독립 모듈이라 병렬 실행 가능하다.

**Goal:** 엔티티만 남아 있던 `notification`, `relationship`, `echo`, `wave` 4개 모듈을 재구축한다.

**Architecture:** 기존 4계층(api/application/domain/infrastructure) 유지. 모듈 간 통신은 Kafka(아웃박스 경유), 동기 조회는 `core` 포트, 실시간 전달은 `MessageBroadcaster`.

**Tech Stack:** Kotlin, Spring Boot 3.5.8, JPA/QueryDSL, PostgreSQL + Flyway, Redisson, Kafka, Kotest + MockK, Testcontainers.

## Global Constraints

- `CODE-CONVENTION.md` 준수. `module/member`, `module/chat` 이 참고 기준이다.
- Postgres 스키마 변경은 `infra/rdb/src/main/resources/migration/V{n}__*.sql` 로만. **기존 V1~V4 수정 금지.** `ddl-auto: validate` 라 엔티티와 정확히 맞아야 기동한다.
- 신규 i18n 키는 `common/src/main/resources/messages_*.properties` **12개 전부**에 등록. 누락 시 키 문자열이 그대로 사용자에게 나간다.
- 도메인은 `require { "i18n.key" }`, 상태코드 변환은 application 계층에서 `LanglezException`.
- 엔티티 `data class` 금지, enum 은 `@Enumerated(STRING)`, 제약에 이름 부여(`UNQ_*`/`IDX_*`).
- 정렬·커서는 `created_at` 이 아니라 **id 시퀀스** 기준(서버 시계 어긋남).
- 인증된 회원 id 는 `@MemberId` 로만 받는다. 본문에서 받으면 남을 사칭할 수 있다.
- **모든 방 단위·소유자 단위 접근은 권한 검사를 거친다(IDOR 방지).**
- 커밋하지 마라. 각자 자기 모듈 밖으로 나가지 마라.
- 시작 기준: `./gradlew build` 270 테스트 통과. 끝날 때도 초록이어야 한다.

---

### Task A: notification — 알림 발송

**현재:** `domain/Notification.kt` 하나뿐. `core.Notificator` 포트의 구현이 없다.

**왜 급한가:** `chat` 이 이미 `chat-message-sent` 를 Kafka 로 쏘고 있는데 **받는 쪽이 없다.** 지금 메시지를 보내도 상대에게 푸시가 안 간다.

**만들 것**
- `api/NotificationConsumer.kt` — `@KafkaListener(topics = ["chat-message-sent"])`
- `application/NotificationService.kt` — 상태 판정 + 발송 + 이력 저장
- `infrastructure/FcmPushSender.kt` — FCM 전송 (외부 SDK 없이 갈 거면 인터페이스만 두고 no-op + 로그, 이유 보고)
- `infrastructure/NotificatorImpl.kt` — `core.Notificator` 구현
- `api/NotificationController.kt` + `NotificationAPI.kt` — 내 알림 목록, 읽음 처리
- 저장소 포트/어댑터

**상태 판정 (핵심)**

`core.OnlineTracker` 가 이미 다 제공한다:
```kotlin
tracker.viewers("/topic/chat/room/{roomId}")  // 그 방을 보는 중
tracker.checkOnline(memberId)                 // 앱 실행 중(핑 10초 TTL)
```
| 상태 | 판정 | 동작 |
|---|---|---|
| 그 방 보는 중 | `viewers` 에 포함 | **아무것도 안 함** (chat 쪽 폴러가 이미 걸러내지만 방어적으로 재확인) |
| 앱 켜짐, 다른 화면 | `checkOnline == true` | 인앱 알림 — `MessageBroadcaster` 로 `/topic/notification/{memberId}` |
| 앱 미사용 | `checkOnline == false` | FCM 푸시 (`Member.fcm` 토큰) |

FCM 토큰은 `member` 모듈에 있다. 직접 조회하지 말고 이벤트 페이로드나 `core` 포트로 받아라 — 어느 쪽을 택했는지 보고.

**테스트:** 세 상태 각각 올바른 경로를 타는지, 알림 이력이 남는지, 토큰이 없으면 조용히 건너뛰는지.

---

### Task B: relationship — 팔로우/차단/신고

**현재:** `Follow`, `Block`, `Report` 엔티티 + `BlockQueryImpl` + 아웃박스 엔티티.

**왜 급한가:** `chat` 이 차단 검사는 하는데 **차단을 등록할 API 가 없다.** `chat-user-reported` 도 받는 쪽이 없어 **신고가 증발한다.**

**만들 것**
- `domain/RelationshipRepository.kt` + `infrastructure/RelationshipRepositoryImpl.kt`
- `application/RelationshipService.kt` — 팔로우/언팔로우/차단/차단해제/신고
- `api/RelationshipController.kt` + `RelationshipAPI.kt` + request/response
- `api/RelationshipConsumer.kt` — `@KafkaListener(topics = ["chat-user-reported"])` → `Report` 저장
- `api/RelationshipEventListener.kt` + `infrastructure/outbox/RelationshipOutBoxScheduler.kt` — `module/member` 의 아웃박스 패턴 그대로. **`@DistributedLock` 필수**

**규칙**
- 차단하면 팔로우 관계는 양방향 해제한다(차단했는데 계속 팔로우 상태면 이상하다).
- 자기 자신 팔로우/차단 금지.
- 팔로워/팔로잉 목록은 커서 페이징.
- `Report.sourceType` 은 `ECHO_POST`, `CHAT_USER` 가 이미 있다. 필요하면 추가.

---

### Task C: echo — 트위터형 피드

**현재:** `Post`, `Comment`, `PostLike`, `PostMedia`, `Hashtag`, `PostHashtag`, `HashtagDailyStat` + 아웃박스.

**만들 것**
- 저장소 포트/어댑터 (QueryDSL)
- `application/EchoService.kt` — 글 작성/삭제, 타임라인, 좋아요, 댓글, 해시태그 추출
- `api/EchoController.kt` + `EchoAPI.kt` + request/response
- 아웃박스 리스너 + 스케줄러(`@DistributedLock` 필수)

**타임라인 설계**
- **홈 타임라인** = 내가 팔로우한 사람의 글. 팔로우 관계는 `relationship` 소유다. 모듈을 직접 참조하지 말고 `core` 포트(`FollowQuery` 같은)를 새로 만들어 relationship 이 구현하게 하거나, 이벤트로 팔로우 그래프를 복제하라. **어느 쪽을 택했는지와 이유를 보고해라.**
- **차단 반영 필수** — `core.BlockQuery` 로 차단한/당한 사람 글을 걸러라.
- 커서 페이징은 post id 기준.

**첨부:** `PostMedia` 가 이미 있다. 업로드는 `core.Storage.presign` → 확정은 **key 로만** 받는다. 클라이언트가 준 URL 을 그대로 저장하면 외부 주소를 심을 수 있다(`module/chat`, `module/profile` 이 같은 패턴).

**해시태그:** 본문에서 추출해 `Hashtag`/`PostHashtag` 로 연결. `HashtagDailyStat` 은 일별 집계 스케줄러.

---

### Task D: wave — 음성방 + 사라지는 채팅

**현재:** `WaveRoom`(JPA), `WaveMessage`(JPA).

**핵심 정책 변경:** wave 채팅은 **저장하지 않는다.** 방이 끝나면 대화도 끝나는 휘발성 채팅이다. Mongo 도 Postgres 도 쓰지 않는다.

**할 것**
1. **`domain/WaveMessage.kt` 삭제** + `V5__drop_wave_messages.sql` 로 `wave_messages` 테이블 제거.
2. 채팅은 **Redis 링버퍼**로만: 방별 최근 N개(예: 200개)만 유지, 방 종료 후 TTL 로 소멸. Redisson `RList`/`RDeque` + `expire`.
3. 실시간 전달은 `core.MessageBroadcaster` 재사용 — `/topic/wave/{roomId}/chat`. **chat 모듈이 쓰는 그 포트 그대로다. 새로 만들지 마라.**
4. `WaveRoom` 은 JPA 유지(방 생명주기·참여자 수 제한은 영속 정보).
5. `application/WaveService.kt`, `api/WaveController.kt` + `WaveAPI.kt`, 저장소 포트/어댑터.
6. WebSocket 구독 인가 — `module/chat/.../config/ChatWebSocketConfiguration.kt` 가 `/topic/chat/room/{id}` 를 참여자만 구독하게 막는다. **wave 토픽도 같은 보호가 필요하다.** 그 파일을 수정하지 말고(다른 소유자), wave 쪽 인가를 어떻게 붙일지 설계해 보고해라.

**주의:** `WaveRoom.kt` 의 `init` 블록이 `LanglezException` 을 던진다. 컨벤션상 도메인은 `require { "i18n.key" }` 를 쓰고 변환은 application 이 한다. 이 참에 맞춰라.

---

### 공통 마무리 (각 태스크가 자기 몫을 한다)

- 신규 i18n 키를 12개 번들 전부에 등록
- `./gradlew build` 통과
- 자기 모듈의 `build.gradle.kts` 의존 정리

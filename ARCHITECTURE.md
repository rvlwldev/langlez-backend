# Langlez Backend — 이벤트 아키텍처

토픽 발행과 수신, 그에 따른 모듈별 처리를 다룬다.
모듈 구조·계층 규약은 `CLAUDE.md`, 구현 상세 규약은 `module/CLAUDE.md`, 현황과 남은 작업은 `README.md` 를 본다.

**이 문서는 실제 코드에서 뽑았다. 코드와 어긋나면 코드가 정답이다.**

---

## 0. 전달 수단이 세 가지다

같은 "알린다"라도 목적이 다르면 경로가 다르다. 섞으면 비용이 새거나 유실된다.

```text
                     유실되면 안 되는가?
                            │
              ┌─────── 예 ──┴── 아니오 ───────┐
              │                               │
     응답을 기다리는가?                 접속 중에만 의미 있나?
              │                               │
     ┌── 예 ──┴── 아니오 ──┐          ┌─ 예 ──┴── 아니오 ─┐
     │                     │          │                   │
  core 포트             Kafka     Redis pub/sub      Redis 직결
  (동기 조회)         (아웃박스)   (→ WebSocket)      (하트비트)
```

| 수단 | 쓰는 곳 | 이유 |
|---|---|---|
| **Kafka** (아웃박스 경유) | 모듈 간 상태 변경 전파 | 저장과 발행이 한 트랜잭션에 묶여야 유실이 원천 차단된다 |
| **`core` 포트** | 응답을 기다려야 하는 조회 | `FollowQuery`, `BlockQuery`, `MemberStatusQuery` 등 |
| **`core.MessageBroadcaster`** | 접속 중인 사용자에게 실시간 전달 | Redis pub/sub → 모든 인스턴스 → WebSocket |
| **Redis 직결** | 고빈도 하트비트 | 접속 핑(5초)을 브로커에 태우면 비용만 든다 |

---

## 1. 전체 그림

```text
  ┌──────────────┐   ┌──────────────┐   ┌──────────────┐   ┌──────────────┐
  │    member    │   │     chat     │   │ relationship │   │     echo     │
  └──────┬───────┘   └──────┬───────┘   └──────┬───────┘   └──────┬───────┘
         │ ①                │ ①                │ ①                │
         ▼                  ▼                  ▼                  ✗ 미배선
  ┌─────────────┐    ┌─────────────┐    ┌─────────────┐    ┌─────────────┐
  │member_event_│    │ chat_event_ │    │relationship_│    │ echo_event_ │
  │   outbox    │    │   outbox    │    │event_outbox │    │   outbox    │
  └──────┬──────┘    └──────┬──────┘    └──────┬──────┘    └─────────────┘
         │ ② 2초             │ ② 2초            │ ② 2초       (쓰는 코드 없음)
         ▼                  ▼                  ▼
  ╔══════════════════════════════════════════════════════════════════╗
  ║                            K A F K A                             ║
  ║  member-created   member-handle-changed   chat-user-reported     ║
  ║  member-followed  chat-message-sent                              ║
  ╚═══════╤═══════════════════════╤══════════════════════════════════╝
          │ ③                     │ ③
          ▼                       ▼
  ┌────────────────┐    ┌────────────────┐          ┌──────────────┐
  │  notification  │    │  relationship  │          │  <소비자 없음> │
  │                │    │                │          │ member-created│
  │ chat-message-  │    │ chat-user-     │          │ member-handle-│
  │ sent           │    │ reported       │          │ changed       │
  │ member-followed│    │                │          └──────────────┘
  └───────┬────────┘    └────────────────┘
          │ ④
          ▼
   Redis pub/sub ──▶ 전 인스턴스 ──▶ WebSocket(STOMP) ──▶ 앱
          │
          └──▶ FCM (오프라인일 때)
```

① 애플리케이션 이벤트 → 아웃박스 행 (같은 트랜잭션)
② 스케줄러가 폴링해 Kafka 로 발행
③ 컨슈머가 수신
④ 실시간 전달

---

## 2. 발행 — 왜 아웃박스를 거치는가

DB 커밋과 Kafka 발행은 한 트랜잭션에 못 묶는다. 그냥 발행하면 **"저장은 됐는데 이벤트는 유실"** 또는 그 반대가 난다.

아웃박스는 발행을 **DB 쓰기로 바꿔서** 원자성을 얻는다.

```text
  Service                     EventListener              OutBoxScheduler
     │                             │                           │
     │ publishEvent(...)           │                           │
     ├────────────────────────────▶│                           │
     │                    @TransactionalEventListener          │
     │                      (phase = BEFORE_COMMIT)            │
     │                             │                           │
     │                             │ repo.save(OutBox)         │
     │                             ├──┐                        │
     │                             │  │ 같은 트랜잭션           │
     │◀────────────────────────────┤◀─┘                        │
     │                                                         │
     ├─── COMMIT ──────────────────────────────────────────────│
     │   (도메인 행 + 아웃박스 행이 함께 커밋되거나 함께 롤백)      │
     │                                                         │
     │                                      2초마다 @Scheduled  │
     │                                    + @DistributedLock    │
     │                                                         │
     │                              fetch(chunk, tries) ◀──────┤
     │                              @Lock(PESSIMISTIC_WRITE)   │
     │                                                         │
     │                              Kafka 발행 ◀───────────────┤
     │                              성공: complete()            │
     │                              실패: fail(tries) 후 재시도  │
```

### `BEFORE_COMMIT` 이어야 하는 이유

`AFTER_COMMIT` 을 쓰면 원 트랜잭션이 이미 닫힌 뒤라 아웃박스 insert 가 **별도 트랜잭션**이 된다. 원본이 롤백돼도 이벤트만 남는 불일치가 생긴다.

### 중복 발행을 막는 것은 `@DistributedLock` 이다

`fetch` 의 비관적 잠금은 **트랜잭션이 fetch 직후 커밋되면서 즉시 풀린다.** 인스턴스가 여러 대일 때 중복 실행을 실제로 막는 건 스케줄러에 걸린 `@DistributedLock` 하나뿐이다.

> **함정으로 실제 터졌던 것:** `OutBoxRepository.fetch` 는 파생 쿼리라 Spring Data 의 기본 트랜잭션이 **안 붙는다**(기본 트랜잭션은 `SimpleJpaRepository` 가 구현하는 CRUD 메서드에만 붙는다). `@Transactional` 없이 잠금 쿼리를 쏘면 `Query requires transaction be in progress` 로 매번 터져 **아웃박스 발행이 전 모듈에서 통째로 멈춘다.** 기존 테스트가 저장소를 목으로 대체해 오래 드러나지 않았다.

### 예외 — 채팅 메시지는 아웃박스 행을 안 만든다

가장 빈번한 쓰기라 별도 행을 만들면 쓰기 증폭이 그대로 두 배가 된다. 대신 **Mongo 문서의 `published` 플래그**로 같은 보장을 얻는다.

```text
  ChatService.send()
     └─▶ Mongo 저장 (published = false)
            │
            ▼
  ChatMessagePublisher   @Scheduled(fixedDelay = 1000)
                         @DistributedLock(throwOnFailure = false)
     findUnpublished(CHUNK)
        └─ 발행 성공 ─▶ markPublished()      ← 성공한 것만 표시
        └─ 발행 실패 ─▶ 그대로 둠 → 다음 주기에 다시 잡힘
```

발행 직전에 **"수신자가 그 방을 보고 있는지"** 를 확인해 보고 있으면 이벤트를 아예 안 낸다. 화면에 이미 떠 있는 메시지 위에 푸시를 겹치지 않기 위해서다.

---

## 3. 토픽 목록

| 토픽 | 발행 | 수신 | 처리 |
|---|---|---|---|
| `chat-message-sent` | chat (`published` 플래그) | **notification** | 3상태 판정 → 인앱 또는 FCM |
| `chat-user-reported` | chat (아웃박스) | **relationship** | `Report` 저장 |
| `member-followed` | relationship (아웃박스) | **notification** | 팔로우 알림 |
| `member-created` | member (아웃박스) | *없음* | — |
| `member-handle-changed` | member (아웃박스) | *없음* | — |

`member-created` / `member-handle-changed` 는 **발행되지만 아무도 안 듣는다.** 소비처가 생길 때까지 브로커에 쌓이기만 한다.

`core/event/echo/` 의 `EchoPostLikedEvent`, `EchoCommentCreatedEvent` 는 **정의만 있고 발행하는 코드가 없다.** `echo_event_outbox` 도 마찬가지로 빈 스캐폴딩이다.

### 파티션 키

```text
  chat-user-reported   →  key = roomId        (방 단위 순서 보장)
  member-followed      →  key = followedId    (한 사람에 대한 이벤트 순서 보장)
  member-created       →  key = memberId
```

같은 키는 같은 파티션으로 가서 순서가 보장된다.

---

## 4. 수신 — 컨슈머의 공통 골격

Kafka 는 **at-least-once** 다. 리밸런싱, 오프셋 커밋 실패, 컨슈머 예외 재시도, 아웃박스의 발행 재시도가 전부 같은 레코드를 다시 흘린다. 방어 없이 두면 알림이 두 번 가고 신고가 두 건 쌓인다.

```text
  ┌─────────────────────────────────────────────────────────────┐
  │  @KafkaListener(topics = [...], groupId = "...")            │
  │                                                             │
  │   payload: String   ← StringDeserializer, JSON 원문         │
  │        │                                                    │
  │        ▼                                                    │
  │   ① dedup.isDuplicate(topic, payload)                       │
  │        │                                                    │
  │        ├─ true  ─▶ return          (조용히 버린다)           │
  │        │                                                    │
  │        └─ false ─▶ 처리 중으로 표시(SETNX) 후 진행           │
  │                    │                                        │
  │        ┌───────────┴──────────── try ──────────────────┐    │
  │        │  ② mapper.readValue(payload, Event::class)    │    │
  │        │  ③ service.처리(...)                          │    │
  │        └───────────┬───────────── catch ──────────────┘    │
  │                    │                                        │
  │                    ▼ 예외                                   │
  │        ④ dedup.release(topic, payload)  ← 표시 되돌림       │
  │           throw e                        ← 재시도/DLT 로     │
  └─────────────────────────────────────────────────────────────┘
```

### ② 역직렬화가 `try` **안**에 있어야 한다

밖에 두면 깨진 페이로드가 왔을 때 **표시가 남은 채 예외가 나가고**, 이후 재시도와 DLT 재투입이 전부 "중복"으로 걸러져 그 메시지가 영영 사라진다. 실제로 그렇게 새고 있던 적이 있다.

### 멱등 키 — 페이로드에 고유 식별자가 있어야 한다

`OutBoxProcessor` 는 `ProducerRecord(topic, key, payload)` 로만 발행한다. **`messageId` 헤더를 안 단다.** 그래서 식별자를 페이로드에서 만든다.

즉 **이벤트 DTO 마다 그 발생 건을 유일하게 가리키는 값이 반드시 들어 있어야 한다.**

```text
  ChatMessageSentEvent  →  messageId    ✓
  MemberFollowedEvent   →  followId     ✓
```

`MemberFollowedEvent` 에 `followId` 를 실은 이유가 이것이다. `(followerId, followedId)` 만으로 키를 잡으면 **언팔로우 후 재팔로우가 같은 값**이라 정상 알림이 막힌다. `Follow` 행은 매번 새 id 를 받으므로 재팔로우는 다른 키가 된다.

### fail-open

Redis 장애로 중복 검사 자체가 실패하면 **통과시킨다.** 중복 알림보다 알림 누락이 나쁘다는 판단이다. 대신 뒤쪽 방어선(신고의 존재 검사, DB 유니크 제약)이 계속 필요하다.

### 한계 — 강제 종료 시 유실

`release` 는 **같은 JVM 에서 `Exception` 이 잡혔을 때만** 돈다. `Error`(OOM)나 SIGKILL·OOMKilled 로 죽으면 표시만 남고 오프셋은 미커밋이라, 재기동 후 재배달이 "중복"으로 걸러져 TTL(1시간)까지 유실된다. 그레이스풀 셧다운은 in-flight 를 기다리므로 정상 배포로는 안 터진다. **감수한 것이지 못 본 게 아니다.**

### 재시도와 DLT

```text
  컨슈머 예외
     └─▶ DefaultErrorHandler 가 seek 후 재호출 (재시도)
            └─▶ 소진되면  <원본토픽>.DLT 로 라우팅
                  파티션은 -1  ← 프로듀서가 분배한다.
                                 소스 파티션 번호를 그대로 쓰면
                                 DLT 파티션이 더 적을 때 던진다
```

---

## 5. 모듈별 처리

### notification — 유일하게 두 토픽을 듣는다

```text
  chat-message-sent ─┐
                     ├─▶ NotificationConsumer ─▶ NotificationService
  member-followed ───┘         (once 헬퍼)
```

**전달 경로 판정 (3상태):**

```text
                    수신자가 그 방을 보고 있나?
                              │
                  ┌──── 예 ───┴─── 아니오 ────┐
                  │                           │
            아무것도 안 함              앱이 켜져 있나?
         (화면에 이미 떠 있다)                │
                              ┌──── 예 ───────┴─── 아니오 ───┐
                              │                              │
                    Redis pub/sub                    FCM 푸시
                    → WebSocket 인앱 알림           (토큰 없으면 조용히 끝)
```

이력을 **먼저** 남기고 전달한다. 전달이 실패해도 알림함에는 남아야 한다.

FCM 전송 실패는 삼킨다 — 죽은 토큰은 재시도해도 같은 결과인데 그동안 파티션이 막혀 뒤에 쌓인 **다른 사람** 알림까지 늦어진다.

**렌더링 규약:** `title` 에 **i18n 메시지 키**를 넣고 `data` 에 id 를 실어 클라이언트가 사용자 언어로 그린다. 서버가 발신자 표시명을 조회하지 않는다 — 핸들이 바뀌어도 알림이 낡지 않는다.

```text
  type  = "MEMBER_FOLLOWED"
  title = "notification.member-followed"   ← 키. 클라이언트가 현지화
  data  = { "followerId": "..." }
```

> **알려진 문제:** FCM `setNotification` 으로 넘긴 제목은 클라이언트를 안 거치고 OS 가 그대로 렌더한다. 지금은 키 원문이 배너에 뜬다.

### relationship — 채팅 신고를 받아 저장한다

```text
  chat-user-reported ─▶ RelationshipConsumer ─▶ RelationshipService.report
                                                     │
                                            existsReport 선검사
                                                     │
                                              Report 저장
```

방 id 를 `sourceId` 로 남긴다. 채팅 신고는 "이 방에서 상대가 이랬다"가 단위라 그래야 운영이 추적한다.

방어가 두 겹이다. `MessageDeduplicator` 가 앞에서 재배달을 걷어내고, 통과해도 `existsReport` 가 같은 신고를 두 행으로 만들지 않는다.

> **아직 열린 구멍:** `existsReport` 는 유니크 제약이 없는 check-then-insert 라 **동시** 요청에는 뚫린다. Redis 장애(fail-open), TTL 만료, 페이로드가 다른 중복 신고 세 경우가 창구다. DB 유니크 제약이 최종 방어선이어야 한다.

### chat — 발행만 하고 아무 토픽도 안 듣는다

두 갈래로 내보낸다.

```text
  메시지 전송
     ├─▶ Mongo 저장 (published = false)
     │      └─▶ ChatMessagePublisher ─▶ chat-message-sent (Kafka)
     │
     └─▶ MessageBroadcaster ─▶ Redis pub/sub ─▶ WebSocket (즉시)
```

**즉시 전달과 알림 발행이 다른 경로다.** 앞의 것은 지금 보고 있는 사람용이고, 뒤의 것은 안 보고 있는 사람용이다.

신고는 아웃박스를 거쳐 `chat-user-reported` 로 나간다.

### echo — 이벤트 배선이 전부 비어 있다

`core/event/echo/` 에 DTO 2종, `echo_event_outbox` 테이블과 엔티티가 있지만 **발행하는 코드도 스케줄러도 없다.** 좋아요·댓글이 알림으로 이어지지 않는다.

팔로우 그래프는 **이벤트로 복제하지 않는다.** `core.FollowQuery` 포트로 그때그때 조회한다 — 같은 데이터를 두 벌 들면 반드시 어긋나고, 언팔로우 이벤트가 없어 복제본은 늘기만 한다.

### member — 발행하지만 듣는 사람이 없다

`member-created`, `member-handle-changed` 를 아웃박스로 내보낸다. 소비자가 없다.

접속 핑은 **Kafka 를 안 탄다.** 5초 간격 고빈도라 브로커 왕복 비용이 가치보다 크다. `MemberPingController` 가 Redis 버킷에 직접 쓴다.

### wave — 브로커를 안 쓴다

음성방 채팅은 Redis 링버퍼에만 남는 휘발성이다. 실시간 전달은 `MessageBroadcaster` 로만 한다.

---

## 6. 실시간 전달 — Redis pub/sub 이 필요한 이유

STOMP 브로커가 인메모리(`enableSimpleBroker`)라 **자기 JVM 에 붙은 세션에만** 전달한다. 인스턴스가 여러 대면 다른 서버에 붙은 상대가 못 받는다.

```text
   인스턴스 A                Redis                 인스턴스 B
      │                       │                        │
  broadcast(topic, payload)   │                        │
      ├──── PUBLISH ─────────▶│                        │
      │                       ├──── SUBSCRIBE ────────▶│
      │◀───── SUBSCRIBE ──────┤                        │
      │                       │                        │
   자기 세션에 push                              자기 세션에 push
      │                                                │
      ▼                                                ▼
   앱(A 에 붙음)                                  앱(B 에 붙음)
```

**그래서 서비스 코드는 `SimpMessagingTemplate` 이 아니라 `core.MessageBroadcaster` 포트를 쓴다.** 직접 쓰면 다중 인스턴스에서 조용히 깨진다.

### 구독 인가는 기본 거부다

```text
  SUBSCRIBE 프레임
        │
        ▼
  WebSocketSubscriptionGate   (common — 모든 구독이 여기를 지난다)
        │
        │  등록된 SubscriptionAuthorizer 중 supports(destination) 가 참인 것을 찾는다
        │
        ├─ 하나도 없음 ─────────▶ 거부          ← 핵심
        │
        └─ 찾음 ─▶ authorize(destination, memberId)
                        ├─ false ─▶ 거부
                        └─ true  ─▶ 통과
```

모듈은 `{Domain}SubscriptionAuthorizer` 로 **"내 토픽은 이렇게 판정한다"만 선언**한다. 인터셉터를 새로 달지 않는다 — 다는 순간 "내 접두사가 아니면 통과"가 생기고, 그때 아무도 책임지지 않는 목적지가 다시 열린다.

| 목적지 | 판정 |
|---|---|
| `/topic/chat/room/{roomId}` | 그 방 참여자인가 |
| `/topic/wave/{roomId}/chat` | 그 방 참여자인가 |
| `/topic/notification/{memberId}` | 본인인가 |

목적지는 **끝을 고정한 정규식**으로만 통과시킨다. 심플 브로커는 구독 목적지에 별표 와일드카드를 허용해서, 방 번호 자리를 느슨하게 열면 전체 방을 한 번에 빨아간다.

---

## 7. 스케줄 한눈에

| 주기 | 무엇 | 분산 락 |
|---|---|---|
| 1초 | `ChatMessagePublisher` — 미발행 메시지 → Kafka | ✓ |
| 2초 | `MemberOutBoxScheduler` | ✓ |
| 2초 | `ChatOutBoxScheduler` | ✓ |
| 2초 | `RelationshipOutBoxScheduler` | ✓ |
| 5초 | `ResilientCacheProvider` 헬스 체크 | ✗ (의도적 — 노드별 폴백 상태를 각자 판단해야 한다) |
| 1분 | `VisitCountSyncScheduler` — Redis → DB | ✓ |
| 5분 | `ChatReconciler` — Mongo↔Postgres 이중 쓰기 창 복구 | ✓ |
| 10분 | `MemberOnlineTracker` 동기화 | ✓ |
| 매일 06시 | `MemberOutBoxHistoryScheduler` — 완료 건 이관 | ✓ |
| 매일 06시 | `RelationshipOutBoxHistoryScheduler` | ✓ |

**`@Scheduled` 에는 `@DistributedLock` 을 반드시 함께 건다.** 서버가 여러 대일 때 중복 실행을 막는 유일한 장치다. 위 표에서 유일한 예외는 이유가 코드 주석에 있다.

> **아직 남은 것:** `chat`·`echo` 는 히스토리 이관 스케줄러가 없어 완료 행이 아웃박스 테이블에 영원히 남는다. 그리고 이관된 `*_outbox_history` 를 **비우는 배치는 아예 없다** — 무한히 증가한다.

---

## 8. 새 이벤트를 추가할 때

```text
  1. 이벤트 DTO 를 core/event/{domain}/ 에 둔다        ← 모듈 간 공유 계약이다
     └─ 그 발생 건을 유일하게 가리키는 id 를 반드시 넣는다 (멱등 키가 된다)

  2. 발행 측
     ├─ Service 에서 ApplicationEventPublisher.publishEvent
     ├─ {Domain}EventListener 가 @TransactionalEventListener(BEFORE_COMMIT) 로 받아
     │  아웃박스 행 저장. 파티션 키를 무엇으로 할지 정한다
     └─ {Domain}OutBoxScheduler + {Domain}OutBoxHistoryScheduler
        (@Scheduled + @DistributedLock. member 모듈이 표준형이다)

  3. 수신 측
     ├─ api/{Domain}Consumer.kt 에 @KafkaListener
     ├─ dedup.isDuplicate → try { readValue; 처리 } catch { release; throw }
     └─ 역직렬화를 try 안에 둔다

  4. 사용자에게 보이면
     ├─ i18n 키를 messages_*.properties 12개 전부에 등록
     └─ title 에는 키를, data 에는 id 를 넣는다
```

**체크리스트에서 빠뜨리기 쉬운 것**

- 아웃박스 행만 만들고 **스케줄러를 안 만들면** 테이블에 쌓이기만 하고 한 건도 안 나간다
- **이벤트에 고유 id 가 없으면** 같은 내용의 두 사건이 하나로 합쳐진다
- 발행만 하고 **소비자를 안 만들면** 브로커에 쌓이기만 한다 (`member-created` 가 지금 그렇다)
- i18n 키를 빠뜨리면 **키 문자열이 그대로 사용자에게 나간다**

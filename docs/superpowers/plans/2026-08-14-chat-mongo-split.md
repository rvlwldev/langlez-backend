# Chat Storage Split + Presence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 채팅 메시지 본문을 MongoDB 로 옮기고 방·참여자 메타는 Postgres 에 남긴다. 유저 핑을 Kafka 대신 Redis 직결로 바꾸고, 두 사람이 같은 방을 보고 있으면 알림을 보내지 않는다.

**Architecture:** 메시지는 Mongo 문서 하나에 첨부까지 임베드해 조회 1회로 끝낸다. 방 목록·안 읽은 수는 Postgres 카운터로 비정규화해 역시 조회 1회. 알림 발행은 별도 아웃박스 테이블 없이 **메시지 문서의 `published` 플래그**로 처리한다(단일 문서 쓰기라 원자적). 접속 여부는 Redis 버킷(핑), 화면 여부는 Redis 셋(STOMP 구독)으로 판정한다.

**Tech Stack:** Kotlin, Spring Boot 3.5.8, Spring Data MongoDB, JPA/QueryDSL, PostgreSQL, Flyway, Redisson, STOMP, Kotest + MockK, Testcontainers(Postgres/Redis/Mongo).

## Global Constraints

- 기존 컨벤션 유지: `CODE-CONVENTION.md` 준수. 4계층, `require { "i18n.key" }`, `data class` 금지(엔티티), 제약 이름 부여.
- 신규 i18n 키는 `common/src/main/resources/messages_*.properties` **12개 전부**에 등록.
- Postgres 스키마 변경은 `infra/rdb/src/main/resources/migration/V{n}__*.sql` 로만. **기존 V1·V2 수정 금지.**
- Mongo 는 **레플리카셋**이 필요하다(트랜잭션·change stream 용). 테스트는 Testcontainers `MongoDBContainer` 가 단일 노드 레플리카셋을 띄운다.
- **메시지 정렬·커서는 `seq`(방별 단조 증가) 기준.** `createdAt` 은 서버 시계가 어긋나면 순서가 뒤집힌다.
- 실시간 전달은 Redis pub/sub(`MessageBroadcaster`), 모듈 간 통신은 Kafka. 이 분담은 그대로 둔다.
- **신고 아웃박스(`ChatOutBox`)는 Postgres 에 그대로 둔다.** 저빈도이고 유실이 허용되지 않는다.

## 알아둘 위험 — 이중 쓰기

전송 시 Mongo(메시지)와 Postgres(방 프리뷰·안 읽은 수)에 나눠 쓴다. 두 저장소를 가로지르는 원자성은 없다.

**쓰기 순서는 Mongo 먼저**여야 한다. Postgres 가 먼저면 실패 시 "목록엔 보이는데 열면 없는 메시지"가 되고, Mongo 가 먼저면 최악이 "방 목록에 안 뜨는 고아 메시지"라 복구 가능하다.

Task 7 에서 **대사(reconciliation) 스케줄러**로 이 창을 메운다. 이걸 빼면 안 된다.

---

## File Structure

```
core/
  OnlineTracker.kt                    ← recordViewing/clearViewing/isViewing 추가

infra/redis/
  (변경 없음)

module/member/
  api/MemberConsumer.kt               ← 삭제 (Kafka 핑 제거)
  api/MemberPingController.kt         ← 신규, Redis 직결
  application/MemberOnlineTracker.kt  ← 구독(viewing) 상태 추가

module/chat/
  domain/ChatMessage.kt               ← Mongo @Document 로 전환, files 임베드
  domain/ChatMessageFile.kt           ← 삭제 (임베드)
  domain/ChatRoomMember.kt            ← unreadCount 추가
  domain/ChatMessageRepository.kt     ← 신규 포트 (Mongo)
  domain/ChatRepository.kt            ← 메시지 관련 메서드 제거
  infrastructure/mongo/*.kt           ← 신규
  infrastructure/ChatRepositoryImpl.kt← 카운터 기반으로 수정
  application/ChatService.kt          ← 전송 흐름 재구성
  application/ChatMessagePublisher.kt ← 신규, published=false 폴러
  application/ChatReconciler.kt       ← 신규, 대사 스케줄러
  config/ChatWebSocketConfiguration.kt← 구독/해제 시 viewing 기록

infra/rdb/src/main/resources/migration/
  V3__chat_unread_counter.sql
```

---

### Task 1: 핑을 Kafka → Redis 직결로

**Files:**
- Delete: `module/member/src/main/kotlin/com/langlez/member/api/MemberConsumer.kt`
- Create: `module/member/src/main/kotlin/com/langlez/member/api/MemberPingController.kt`
- Test: `module/member/src/test/kotlin/com/langlez/member/api/MemberPingControllerTest.kt`

**Interfaces:**
- Produces: `POST /api/v1/members/me/ping` → 204. `@MemberId` 로 회원을 식별하고 `OnlineTracker.toOnline(id)` 만 호출한다.

**왜:** 5초 하트비트가 Kafka 를 거치면 브로커 왕복 + 컨슈머 + handle→id 조회가 매번 붙는다. 동시 접속 1만이면 초당 2천 건이다. 접속 판정은 Redis 버킷 하나면 되고, 이미 `toOnline` 이 그 일을 한다. handle→id 변환도 사라진다(토큰에 id 가 있다).

- [ ] **Step 1: 실패 테스트**

```kotlin
class MemberPingControllerTest : BehaviorSpec({
    val tracker = mockk<OnlineTracker>(relaxed = true)
    val controller = MemberPingController(tracker)

    Given("앱이 살아 있다는 핑이 오면") {
        Then("접속 상태만 갱신하고 204 를 준다") {
            controller.ping(1L)
            verify { tracker.toOnline(1L) }
        }
    }
})
```

- [ ] **Step 2: 실패 확인** — `./gradlew :module:member:test --tests "*MemberPingControllerTest*"` → Unresolved reference
- [ ] **Step 3: 구현**

```kotlin
@RestController
@RequestMapping("/api/v1/members")
class MemberPingController(private val tracker: OnlineTracker) {

    /**
     * 앱 생존 신호. 5초 간격이라 Kafka 를 거치지 않고 Redis 로 직행한다.
     * TTL(10초) 안에 다시 오지 않으면 자동으로 오프라인이 된다.
     */
    @PostMapping("/me/ping")
    @ResponseStatus(NO_CONTENT)
    fun ping(@MemberId memberId: Long) = tracker.toOnline(memberId)
}
```

- [ ] **Step 4: 통과 확인**, `MemberConsumer` 삭제 후 `./gradlew :module:member:test` 전체 green
- [ ] **Step 5: 커밋** — `refactor(member): 핑을 카프카 대신 레디스 직결로`

---

### Task 2: 화면(viewing) 상태 추적

**Files:**
- Modify: `core/src/main/kotlin/com/langlez/core/OnlineTracker.kt`
- Modify: `module/member/src/main/kotlin/com/langlez/member/application/MemberOnlineTracker.kt`
- Modify: `module/chat/src/main/kotlin/com/langlez/chat/config/ChatWebSocketConfiguration.kt`
- Test: `module/member/.../MemberOnlineTrackerViewingTest.kt`, chat 쪽 인터셉터 테스트

**Interfaces:**
- Produces (core 포트에 추가):
```kotlin
fun recordViewing(memberId: Long, topic: String)
fun clearViewing(memberId: Long, topic: String)
fun clearAllViewing(memberId: Long)
fun viewers(topic: String): Set<Long>
```
Redis 키: `viewing:{topic}` → `Set<Long>`, TTL 5분(하트비트로 갱신). 역방향 `viewing:member:{id}` → `Set<String>` 도 둬야 `DISCONNECT` 에서 정리할 수 있다.

**왜:** 핑은 "앱이 켜져 있다"까지만 말한다. 상대가 그 채팅방을 **보고 있는지**는 STOMP 구독이 유일한 신호다. 구독 인터셉터에 이미 훅이 있다.

- [ ] **Step 1: 실패 테스트** — 구독 기록 후 `viewers(topic)` 에 포함, 해제 후 제외, `clearAllViewing` 이 그 회원의 모든 토픽에서 제거
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `ChatWebSocketConfiguration` 의 `SUBSCRIBE` 분기에서 `recordViewing`, `UNSUBSCRIBE` 에서 `clearViewing`, `SessionDisconnectEvent` 리스너에서 `clearAllViewing`
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): STOMP 구독으로 화면 상태 추적`

---

### Task 3: 메시지를 Mongo 문서로 (첨부 임베드)

**Files:**
- Rewrite: `module/chat/src/main/kotlin/com/langlez/chat/domain/ChatMessage.kt`
- Delete: `.../domain/ChatMessageFile.kt`, `.../infrastructure/jpa/ChatMessageJpaRepository.kt`, `.../infrastructure/jpa/ChatMessageFileJpaRepository.kt`
- Create: `.../domain/ChatMessageRepository.kt`, `.../infrastructure/mongo/ChatMessageRepositoryImpl.kt`, `.../infrastructure/mongo/ChatMessageMongoRepository.kt`
- Modify: `module/chat/build.gradle.kts` (mongodb 의존 추가)
- Test: `.../infrastructure/mongo/ChatMessageRepositoryImplTest.kt` (Testcontainers Mongo)

**Interfaces:**
```kotlin
@Document(collection = "chat_messages")
class ChatMessage(
    val roomId: Long,
    val senderId: Long,
    val seq: Long,                       // 방별 단조 증가. 정렬·커서 기준
    val type: Type,
    val content: String? = null,
    val files: List<Attachment> = emptyList(),   // 임베드
    val createdAt: Instant = Instant.now(),
) {
    @Id var id: String? = null
    var deletedAt: Instant? = null; private set
    var published: Boolean = false; private set   // 알림 발행 여부 = 아웃박스 대용

    fun delete(requesterId: Long, now: Instant = Instant.now())
    fun markPublished()
    fun isDeleted(): Boolean
    class Attachment(val url: String, val sequence: Int)
    enum class Type { TEXT, IMAGE, VIDEO, AUDIO }
}

interface ChatMessageRepository {
    fun nextSeq(roomId: Long): Long                       // Redis INCR 또는 Mongo findAndModify
    fun save(message: ChatMessage): ChatMessage
    fun find(id: String): ChatMessage?
    fun findByRoom(roomId: Long, size: Int, cursor: Long?): List<ChatMessage>  // seq desc
    fun findUnpublished(limit: Int): List<ChatMessage>
    fun countAfter(roomId: Long, seq: Long): Long
}
```

인덱스: `{roomId: 1, seq: -1}`, `{published: 1}` (부분 인덱스), `{roomId: 1, createdAt: -1}`.

**왜 `published` 를 문서에 두나:** 별도 아웃박스 테이블을 쓰면 메시지마다 행이 하나 더 생겨 **가장 빈번한 쓰기가 2배**가 된다. 단일 문서 쓰기는 Mongo 에서 원자적이라, 플래그를 문서 안에 두면 트랜잭션 없이 같은 보장을 얻는다.

- [ ] **Step 1: 실패 테스트** — 저장 후 첨부까지 한 번의 조회로 복원, `findByRoom` 이 seq 내림차순 + 커서 페이징, `findUnpublished` 가 미발행만 반환
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 메시지를 Mongo 문서로 전환하고 첨부 임베드`

---

### Task 4: 안 읽은 수 카운터 비정규화

**Files:**
- Create: `infra/rdb/src/main/resources/migration/V3__chat_unread_counter.sql`
- Modify: `.../domain/ChatRoomMember.kt` (`unreadCount` 추가), `.../domain/ChatRepository.kt`, `.../infrastructure/ChatRepositoryImpl.kt`
- Test: 기존 `ChatRepositoryImplTest` 확장

**Interfaces:**
```kotlin
// ChatRoomMember
var unreadCount: Long = 0
fun increaseUnread() { unreadCount++ }
fun markRead(at: Instant) { ...; unreadCount = 0 }

// ChatRepository — 메시지 관련 제거, 카운터 기반으로
fun findRoomSummaries(memberId: Long, size: Int, cursor: Instant?): List<ChatRoomSummary>
// 제거: saveMessage, findMessage, findMessages, findFiles, countUnread
```

**왜:** 메시지가 Mongo 로 가면 안 읽은 수를 Postgres 조인으로 셀 수 없다. 카운터를 참여자 행에 두면 방 목록이 **여전히 쿼리 1회**로 끝난다. 이게 없으면 방 50개당 Mongo 집계 50번이 붙는다.

- [ ] **Step 1: 실패 테스트** — 전송 시 상대 카운터 +1, 읽음 시 0
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** + V3 마이그레이션 (`ALTER TABLE chat_room_members ADD COLUMN unread_count bigint not null default 0`)
- [ ] **Step 4: 통과 확인** — `MemberIntegrationTest` 로 Flyway/validate 검증
- [ ] **Step 5: 커밋** — `feat(chat): 안 읽은 수 카운터 비정규화`

---

### Task 5: ChatService 전송 흐름 재구성

**Files:**
- Modify: `.../application/ChatService.kt`
- Test: `.../application/ChatServiceTest.kt`, `ChatServiceActionsTest.kt`

**새 전송 순서 (순서가 중요하다):**

```kotlin
fun send(...): ChatMessageView {
    // 1. 검증 (참여자·차단·빈 메시지) — 기존 그대로
    // 2. storage.attach — 트랜잭션 밖, 블로킹 I/O
    // 3. Mongo 저장 (published=false)  ← 먼저. 여기 실패하면 아무것도 안 남는다
    val message = messages.save(ChatMessage(roomId, memberId, messages.nextSeq(roomId), type, content, attachments))
    // 4. Postgres 트랜잭션: 방 프리뷰 갱신 + 상대 unreadCount++ + 상대 rejoin
    //    실패해도 메시지는 Mongo 에 남는다 → Task 7 대사 스케줄러가 복구
    // 5. Redis 브로드캐스트
}
```

**Task 6 의 알림 판정은 여기서 하지 않는다.** 이 서비스는 "메시지 갔다"만 알리고, 보낼지 말지는 발행 시점에 정한다.

- [ ] **Step 1: 실패 테스트** — Mongo 저장이 Postgres 갱신보다 먼저 호출되는지(`verifyOrder`), Postgres 실패 시에도 메시지가 남는지, 나간 상대 rejoin
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `refactor(chat): 전송 흐름을 Mongo 우선으로 재구성`

---

### Task 6: 알림 발행 + 같은 방 보는 중이면 생략

**Files:**
- Create: `.../application/ChatMessagePublisher.kt`
- Modify: `.../api/ChatEventListener.kt` (메시지 이벤트 제거, 신고만 남김)
- Test: `.../application/ChatMessagePublisherTest.kt`

**Interfaces:**
```kotlin
@Scheduled(fixedDelay = 1000)
@DistributedLock(prefix = "lock:chat-message-publish", throwOnFailure = false)
fun publish() {
    messages.findUnpublished(CHUNK).forEach { message ->
        val recipient = /* 방 참여자 중 sender 아닌 쪽 */
        // 둘 다 그 방을 보고 있으면 알림이 필요 없다. 메시지는 이미 WS 로 화면에 떴다.
        if (tracker.viewers(topic(message.roomId)).contains(recipient)) {
            message.markPublished(); messages.save(message); return@forEach
        }
        kafka.send(ChatMessageSentEvent(...))   // notification 모듈이 상태 보고 푸시/인앱 결정
        message.markPublished(); messages.save(message)
    }
}
```

**왜 여기서 판정하나:** 전송 시점에 정하면 그 사이 상대가 방에 들어오거나 나가는 변화를 못 잡는다. 발행 직전이 가장 최신이다.

**왜 아웃박스 테이블이 없나:** `published` 플래그가 그 역할을 한다. 발행 실패 시 플래그가 `false` 로 남아 다음 주기에 재시도된다. 메시지 저장과 "발행해야 함" 표시가 같은 문서라 원자적이다.

- [ ] **Step 1: 실패 테스트** — (a) 상대가 그 방을 보고 있으면 Kafka 발행 없이 published 만 표시, (b) 안 보고 있으면 발행, (c) 발행 실패 시 published 가 false 로 남음
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 화면 상태 기반 알림 발행`

---

### Task 7: 이중 쓰기 대사(reconciliation)

**Files:**
- Create: `.../application/ChatReconciler.kt`
- Test: `.../application/ChatReconcilerTest.kt`

**왜 필요한가:** Task 5 의 3~4단계 사이에서 프로세스가 죽으면 Mongo 에는 메시지가 있는데 Postgres 방 메타(프리뷰·안 읽은 수)에는 반영되지 않는다. 그 방은 목록에서 낡은 상태로 보인다. 저장소가 둘이면 이 창은 원리상 없앨 수 없고, **주기적으로 메워야** 한다.

```kotlin
@Scheduled(cron = "0 */5 * * * *")
@DistributedLock(prefix = "lock:chat-reconcile", throwOnFailure = false)
fun reconcile() {
    // 최근 N분 내 Mongo 메시지 중 방의 last_message_at 보다 새로운 것을 찾아
    // 방 프리뷰와 안 읽은 수를 다시 계산해 맞춘다. 멱등해야 한다.
}
```

- [ ] **Step 1: 실패 테스트** — Postgres 갱신을 건너뛴 상태를 만들고, 대사 후 프리뷰·카운터가 맞아지는지. 두 번 돌려도 결과가 같은지(멱등)
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 이중 쓰기 대사 스케줄러`

---

### Task 8: 나머지 정리 + 전체 검증

- [ ] `ChatController`/`ChatAPI` 의 메시지 id 타입 `Long` → `String` (Mongo ObjectId)
- [ ] `Report.triggerMessageId` 가 이미 `String?` 이라 그대로 맞는지 확인
- [ ] Mongo 접속 설정을 `application.yml` 에 추가, 통합테스트에 `MongoDBContainer` 추가
- [ ] 신규 i18n 키 12개 번들 등록
- [ ] `./gradlew build` 전체 통과
- [ ] 커밋 — `chore(chat): Mongo 전환 마무리`

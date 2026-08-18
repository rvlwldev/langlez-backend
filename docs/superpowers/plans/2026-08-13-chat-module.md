# Chat Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 1:1 채팅 모듈을 Postgres/JPA 기반으로 완전 구현한다 — 방 생성·목록·메시지 조회·읽음·첨부(앨범)·나가기·삭제·신고 + WebSocket 실시간 + Redis pub/sub 팬아웃.

**Architecture:** `module/chat` 은 CODE-CONVENTION.md 의 4계층(api/application/domain/infrastructure)을 따른다. 실시간 전달은 `core.MessageBroadcaster` 포트 뒤에 두고, 어댑터를 Redis `RTopic` 구독 + STOMP 전달로 구현해 다중 인스턴스에서도 동작하게 한다. 차단 조회는 `core.BlockQuery` 포트로 뽑아 relationship 모듈이 구현한다.

**Tech Stack:** Kotlin, Spring Boot 3.5.8, JPA/Hibernate + QueryDSL, PostgreSQL, Flyway, Redisson(RTopic), STOMP over WebSocket, Kotest BehaviorSpec + MockK, Testcontainers.

## Global Constraints

- Mongo 를 쓰지 않는다. 채팅 데이터는 전부 Postgres.
- 스키마 변경은 `infra/rdb/src/main/resources/migration/V{n}__*.sql` 로만. 기존 V 파일 수정 금지. `ddl-auto: validate`.
- 메시지 정렬·커서는 `created_at` 이 아니라 **id 시퀀스** 기준 (서버 시계 어긋남 방지).
- 도메인은 `require { "i18n.key" }`, 상태코드 변환은 application 계층에서 `LanglezException`.
- 신규 i18n 키는 `common/src/main/resources/messages_*.properties` **12개 전부**에 등록.
- 엔티티는 `data class` 금지, enum 은 `@Enumerated(STRING)`, 제약조건에 이름 부여(`UNQ_*`, `IDX_*`).
- LAZY 연관을 가진 엔티티는 캐시하지 않는다.
- 나가기 정책: **재입장(텔레그램식)** — 나가도 이전 대화 전부 보임.
- 사진: **메시지 1건에 N장** (첨부 테이블 분리).
- 메시지 삭제: **모두에게 삭제** (`deletedAt` + 실시간 통지).
- 신고: `Report.sourceType = CHAT_USER`, `sourceId = roomId`, `triggerMessageId` 사용.
- **모듈 간 통신은 Kafka(아웃박스 경유).** 채팅이 notification/relationship 을 직접 호출하지 않는다.
  `ApplicationEventPublisher` 로 도메인 이벤트 발행 → `@TransactionalEventListener(BEFORE_COMMIT)` 가
  `ChatOutBox` 행을 남김 → `ChatOutBoxScheduler` 가 카프카로 발행 (member 모듈과 동일 패턴).
  이벤트 DTO 는 `core/event/chat/` 에 `data class` 로 둔다.
- **예외: 동기 조회는 포트로.** 차단 여부(`BlockQuery`)는 전송을 **막아야** 하므로 응답을 기다려야 한다.
  Kafka 는 이벤트/명령용이지 질의용이 아니다. 조회는 `core` 포트로 뽑는다.

---

## File Structure

```
core/
  BlockQuery.kt                     [생성됨] 차단 조회 포트
  MessageBroadcaster.kt             [생성됨] 실시간 전달 포트

infra/redis/
  broadcast/RedisMessageBroadcaster.kt   RTopic 발행 + 구독 → STOMP 전달
  broadcast/BroadcastEnvelope.kt         토픽+페이로드 봉투

module/relationship/
  infrastructure/BlockQueryImpl.kt       BlockQuery 구현
  infrastructure/jpa/BlockJpaRepository.kt

module/chat/
  domain/ChatRoom.kt                 방 (JPA)
  domain/ChatRoomMember.kt           참여자 (lastReadAt, leftAt)
  domain/ChatMessage.kt              메시지 (JPA, deletedAt)
  domain/ChatMessageFile.kt          첨부 (앨범 N장)
  domain/ChatRepository.kt           포트
  infrastructure/ChatRepositoryImpl.kt
  infrastructure/jpa/*.kt
  application/ChatService.kt         유스케이스
  application/ChatEvents.kt          브로드캐스트 페이로드
  api/ChatController.kt              REST
  api/ChatAPI.kt                     Swagger
  api/request/*.kt  api/response/*.kt
  config/ChatWebSocketConfiguration.kt

infra/rdb/src/main/resources/migration/
  V2__chat.sql
```

---

### Task 1: 스키마 + 도메인 엔티티

**Files:**
- Create: `infra/rdb/src/main/resources/migration/V2__chat.sql`
- Create: `module/chat/src/main/kotlin/com/langlez/chat/domain/ChatRoom.kt`
- Create: `.../domain/ChatRoomMember.kt`
- Create: `.../domain/ChatMessage.kt`
- Create: `.../domain/ChatMessageFile.kt`
- Delete: 기존 Mongo `ChatRoom.kt`, `ChatMessage.kt`
- Modify: `module/chat/build.gradle.kts` (mongodb 의존 제거, querydsl ksp 추가)
- Test: `.../domain/ChatDomainTest.kt`

**Interfaces:**
- Produces: `ChatRoom(id: Long, createdAt: Instant)`, `ChatRoomMember(roomId: Long, memberId: Long, lastReadAt: Instant?, leftAt: Instant?)` with `markRead(at)`, `leave(at)`, `rejoin()`; `ChatMessage(roomId, senderId, type, content, deletedAt)` with `delete(requesterId)`; `ChatMessage.Type { TEXT, IMAGE, VIDEO, AUDIO }`; `ChatMessageFile(messageId, url, sequence)`

- [ ] **Step 1: 실패 테스트 작성**

```kotlin
class ChatDomainTest : BehaviorSpec({
    Given("메시지를 보낸 사람이 삭제하면") {
        val m = ChatMessage(roomId = 1L, senderId = 10L, type = ChatMessage.Type.TEXT, content = "oops")
        m.delete(requesterId = 10L)
        Then("삭제 시각이 남고 내용이 가려진다") {
            m.deletedAt.shouldNotBeNull()
            m.isDeleted() shouldBe true
        }
    }
    Given("남이 삭제하려 하면") {
        val m = ChatMessage(roomId = 1L, senderId = 10L, type = ChatMessage.Type.TEXT, content = "hi")
        Then("거부된다") { shouldThrow<IllegalArgumentException> { m.delete(requesterId = 99L) } }
    }
    Given("참여자가 나갔다가 상대가 메시지를 보내면") {
        val p = ChatRoomMember(roomId = 1L, memberId = 10L).apply { leave(Instant.now()) }
        p.rejoin()
        Then("재입장하며 이전 대화가 그대로 보인다(leftAt 해제)") { p.leftAt shouldBe null }
    }
})
```

- [ ] **Step 2: 실패 확인** — `./gradlew :module:chat:test --tests "*ChatDomainTest*"` → Unresolved reference

- [ ] **Step 3: 엔티티 구현**

```kotlin
@Entity @Table(name = "chat_rooms")
class ChatRoom(
    @Id @GeneratedValue(strategy = IDENTITY) val id: Long = 0,
    var lastMessageAt: Instant? = null,
    @Column(length = 200) var lastMessagePreview: String? = null,
    @CreatedDate val createdAt: Instant = Instant.now(),
) { fun onMessage(preview: String, at: Instant) { lastMessagePreview = preview.take(200); lastMessageAt = at } }

@Entity @Table(name = "chat_room_members",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_CHAT_ROOM_MEMBER", columnNames = ["room_id", "member_id"])],
    indexes = [Index(name = "IDX_CHAT_ROOM_MEMBER_MEMBER", columnList = "member_id")])
class ChatRoomMember(
    @Column(name = "room_id") val roomId: Long,
    @Column(name = "member_id") val memberId: Long,
    @Column(name = "last_read_at") var lastReadAt: Instant? = null,
    @Column(name = "left_at") var leftAt: Instant? = null,
) {
    @Id @GeneratedValue(strategy = IDENTITY) val id: Long = 0
    fun markRead(at: Instant) { if (lastReadAt == null || at > lastReadAt) lastReadAt = at }
    fun leave(at: Instant = Instant.now()) { leftAt = at }
    /** 재입장 정책: 나가도 이전 대화는 그대로 보인다. leftAt 만 해제한다. */
    fun rejoin() { leftAt = null }
    fun hasLeft(): Boolean = leftAt != null
}

@Entity @Table(name = "chat_messages",
    indexes = [Index(name = "IDX_CHAT_MESSAGE_ROOM", columnList = "room_id, id")])
class ChatMessage(
    @Column(name = "room_id") val roomId: Long,
    @Column(name = "sender_id") val senderId: Long,
    @Enumerated(STRING) val type: Type,
    @Column(columnDefinition = "TEXT") val content: String? = null,
    val createdAt: Instant = Instant.now(),
) {
    @Id @GeneratedValue(strategy = IDENTITY) val id: Long = 0
    @Column(name = "deleted_at") var deletedAt: Instant? = null
        protected set

    fun delete(requesterId: Long, now: Instant = Instant.now()) {
        require(senderId == requesterId) { "chat.message.not-owner" }
        require(deletedAt == null) { "chat.message.already-deleted" }
        deletedAt = now
    }
    fun isDeleted(): Boolean = deletedAt != null
    enum class Type { TEXT, IMAGE, VIDEO, AUDIO }
}

@Entity @Table(name = "chat_message_files",
    indexes = [Index(name = "IDX_CHAT_MESSAGE_FILE_MESSAGE", columnList = "message_id, sequence")])
class ChatMessageFile(
    @Column(name = "message_id") val messageId: Long,
    @Column(nullable = false, length = 1000) val url: String,
    val sequence: Int,
) { @Id @GeneratedValue(strategy = IDENTITY) val id: Long = 0 }
```

- [ ] **Step 4: V2 마이그레이션 작성** — 위 4개 테이블 DDL. `chat_event_outbox*` 는 V1 에 이미 있으니 건드리지 않는다. 기존 Mongo 용 테이블 없음.

- [ ] **Step 5: 통과 확인** — `./gradlew :module:chat:test`

- [ ] **Step 6: 커밋** — `feat(chat): 채팅 도메인 엔티티와 스키마 추가`

---

### Task 2: 저장소 포트 + 어댑터

**Files:**
- Create: `.../domain/ChatRepository.kt`, `.../infrastructure/ChatRepositoryImpl.kt`, `.../infrastructure/jpa/{ChatRoomJpaRepository, ChatRoomMemberJpaRepository, ChatMessageJpaRepository, ChatMessageFileJpaRepository}.kt`
- Test: `.../infrastructure/ChatRepositoryImplTest.kt` (Testcontainers)

**Interfaces:**
- Consumes: Task 1 엔티티
- Produces:
```kotlin
interface ChatRepository {
    fun findRoomBetween(a: Long, b: Long): ChatRoom?
    fun createRoom(a: Long, b: Long): ChatRoom
    fun findRoom(roomId: Long): ChatRoom?
    fun findParticipant(roomId: Long, memberId: Long): ChatRoomMember?
    fun findParticipants(roomId: Long): List<ChatRoomMember>
    fun saveParticipant(p: ChatRoomMember): ChatRoomMember
    /** 마지막 메시지 최신순. 나간 방도 상대가 보내면 재등장하므로 leftAt 필터 안 함 */
    fun findRoomSummaries(memberId: Long, size: Int, cursor: Instant?): List<ChatRoomSummary>
    fun saveMessage(message: ChatMessage, fileUrls: List<String>): ChatMessage
    fun findMessage(id: Long): ChatMessage?
    /** id 내림차순 커서 페이징. created_at 을 쓰면 서버 시계 차이로 순서가 뒤집힌다 */
    fun findMessages(roomId: Long, size: Int, cursor: Long?): List<ChatMessage>
    fun findFiles(messageIds: Collection<Long>): Map<Long, List<ChatMessageFile>>
    fun countUnread(roomId: Long, after: Instant?): Long
}
data class ChatRoomSummary(val room: ChatRoom, val partnerId: Long, val unreadCount: Long)
```

- [ ] **Step 1: 실패 테스트** — Testcontainers Postgres + Flyway. `createRoom` 후 `findRoomBetween` 이 같은 방을 돌려주고, 순서 무관(`findRoomBetween(b,a)`)해도 찾아야 한다. `findMessages` 는 id 내림차순.
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — QueryDSL 로 요약/커서 조회. `findRoomSummaries` 는 방-참여자 조인 + 상대 id + `countUnread` 를 **한 쿼리**로 (N+1 금지).
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 채팅 저장소 포트와 QueryDSL 어댑터`

---

### Task 3: 차단 조회 포트 구현 (relationship)

**Files:**
- Create: `module/relationship/src/main/kotlin/com/langlez/relationship/infrastructure/jpa/BlockJpaRepository.kt`
- Create: `.../infrastructure/BlockQueryImpl.kt`
- Test: `.../infrastructure/BlockQueryImplTest.kt`

**Interfaces:**
- Produces: `core.BlockQuery.isBlockedBetween(memberId, otherId): Boolean` 구현 빈

- [ ] **Step 1: 실패 테스트** — A가 B를 차단 → `isBlockedBetween(A,B)`, `isBlockedBetween(B,A)` 둘 다 true. 무관한 쌍은 false.
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
```kotlin
interface BlockJpaRepository : JpaRepository<Block, Long> {
    fun existsByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Boolean
}
@Component
class BlockQueryImpl(private val jpa: BlockJpaRepository) : BlockQuery {
    override fun isBlockedBetween(memberId: Long, otherId: Long): Boolean =
        jpa.existsByBlockerIdAndBlockedId(memberId, otherId) ||
            jpa.existsByBlockerIdAndBlockedId(otherId, memberId)
}
```
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(relationship): BlockQuery 포트 구현`

---

### Task 4: Redis pub/sub 브로드캐스터

**Files:**
- Create: `infra/redis/src/main/kotlin/com/langlez/redis/broadcast/BroadcastEnvelope.kt`
- Create: `.../broadcast/RedisMessageBroadcaster.kt`
- Modify: `infra/redis/build.gradle.kts` (websocket 의존 추가)
- Test: `.../broadcast/RedisMessageBroadcasterTest.kt` (Testcontainers Redis, 인스턴스 2개 시뮬레이션)

**Interfaces:**
- Consumes: `core.MessageBroadcaster`
- Produces: `RedisMessageBroadcaster` 빈. 발행 시 RTopic 으로 나가고, 구독한 **모든 인스턴스**가 각자 STOMP 로 전달.

- [ ] **Step 1: 실패 테스트**
```kotlin
Given("인스턴스 2개가 같은 레디스를 보고 있을 때") {
    val stompA = mockk<SimpMessagingTemplate>(relaxed = true)
    val stompB = mockk<SimpMessagingTemplate>(relaxed = true)
    val a = RedisMessageBroadcaster(redisson, stompA).also { it.subscribe() }
    val b = RedisMessageBroadcaster(redisson, stompB).also { it.subscribe() }
    When("A 에서 발행하면") {
        a.broadcast("/topic/chat/1", mapOf("text" to "hi"))
        Then("B 에 붙은 세션에도 전달된다") {
            eventually(3.seconds) { verify { stompB.convertAndSend("/topic/chat/1", any<Any>()) } }
        }
    }
}
```
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `redisson.getTopic(CHANNEL)` 발행/구독. `@PostConstruct subscribe()`, `@PreDestroy` 해제. 봉투에 `topic` + `payload`(JSON) 담고, 수신 측에서 `SimpMessagingTemplate.convertAndSend(topic, payload)`.
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(redis): 다중 인스턴스 팬아웃 브로드캐스터`

---

### Task 5: ChatService — 방 생성/목록/조회/전송

**Files:**
- Create: `.../application/ChatService.kt`, `.../application/ChatEvents.kt`
- Test: `.../application/ChatServiceTest.kt`

**Interfaces:**
- Consumes: `ChatRepository`, `core.BlockQuery`, `core.MessageBroadcaster`, `core.Storage`,
  `MemberRepository`, `ApplicationEventPublisher`
  (알림은 `Notificator` 직접 호출이 아니라 `ChatMessageSentEvent` 발행 → 아웃박스 → 카프카 → notification 모듈)
- Produces:
```kotlin
fun getOrCreateRoom(memberId: Long, partnerId: Long): ChatRoom
fun listRooms(memberId: Long, size: Int, cursor: Instant?): List<ChatRoomSummary>
fun listMessages(memberId: Long, roomId: Long, size: Int, cursor: Long?): List<ChatMessageView>
fun send(memberId: Long, roomId: Long, type: ChatMessage.Type, content: String?, keys: List<String>): ChatMessageView
```

- [ ] **Step 1: 실패 테스트** — 최소 5개:
  1. 같은 상대와 두 번 요청해도 방이 하나 (`getOrCreateRoom` 재사용)
  2. 자기 자신과는 방 생성 불가 (400)
  3. 차단된 상대와는 방 생성 불가 (403)
  4. 방 참여자가 아니면 메시지 조회 불가 (403)
  5. 전송 시 상대가 나갔으면 `rejoin()` 되어 방이 재등장
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — 전송 흐름:
  권한 검사 → 차단 검사 → `storage.attach(key)` 로 첨부 확정(**트랜잭션 밖**, 블로킹 I/O)
  → 트랜잭션 안에서 메시지+파일 저장, 방 `onMessage`, 상대 `rejoin()`,
    `publisher.publishEvent(ChatMessageSentEvent(...))` (BEFORE_COMMIT 리스너가 아웃박스 기록)
  → 커밋 후 `broadcaster.broadcast` (실시간은 즉시성이 중요해 카프카를 안 거친다)
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 방 생성·목록·조회·전송`

---

### Task 5b: 도메인 이벤트 + 아웃박스 리스너

**Files:**
- Create: `core/src/main/kotlin/com/langlez/core/event/chat/ChatEvents.kt`
- Create: `module/chat/src/main/kotlin/com/langlez/chat/api/ChatEventListener.kt`
- Create: `.../infrastructure/outbox/ChatOutBoxHistory` 확인, `.../infrastructure/jpa/ChatOutBoxRepository.kt`
- Create: `.../application/ChatOutBoxScheduler.kt`
- Test: `.../api/ChatEventListenerTest.kt`

**Interfaces:**
- Produces (core/event/chat):
```kotlin
data class ChatMessageSentEvent(val roomId: Long, val messageId: Long, val senderId: Long, val recipientId: Long, val preview: String)
data class ChatUserReportedEvent(val roomId: Long, val reporterId: Long, val reportedUserId: Long, val reason: String, val triggerMessageId: Long?)
```
- 리스너는 member 모듈과 동일하게 `@TransactionalEventListener(BEFORE_COMMIT)` 로 `ChatOutBox` 저장.
- 스케줄러는 `OutBoxProcessor<ChatOutBox>` 상속 + `@Scheduled` + **`@DistributedLock` 필수**
  (`OutBoxRepository` KDoc 참고 — 중복 발행을 막는 건 SKIP LOCKED 가 아니라 이 락이다).

- [ ] **Step 1: 실패 테스트** — 이벤트 발행 시 `chat_event_outbox` 에 `topic="chat-message-sent"`, `key=roomId` 로 행이 남는가
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 도메인 이벤트 아웃박스 발행`

---

### Task 6: 읽음 / 나가기 / 삭제 / 신고

**Files:**
- Modify: `.../application/ChatService.kt`
- Modify: `module/relationship/.../Report.kt` (SourceType 에 값 추가 불필요 — `CHAT_USER` 이미 있음. 확인만)
- Test: `.../application/ChatServiceActionsTest.kt`

**Interfaces:**
- Produces:
```kotlin
fun markRead(memberId: Long, roomId: Long, at: Instant = Instant.now())
fun leaveRoom(memberId: Long, roomId: Long)
fun deleteMessage(memberId: Long, messageId: Long)
fun report(memberId: Long, roomId: Long, reason: String, triggerMessageId: Long?)
```

- [ ] **Step 1: 실패 테스트**
  1. 읽음 처리 후 `unreadCount` 가 0
  2. 나가면 `leftAt` 이 찍히고 내 목록에서 사라짐
  3. 나간 뒤 상대가 보내면 재등장 + **이전 대화도 보임**(재입장 정책)
  4. 남의 메시지는 삭제 불가 (403)
  5. 삭제하면 양쪽 모두 `[삭제된 메시지]` 로 보이고 실시간 통지가 나감
  6. 신고 시 relationship 을 직접 부르지 않고 `ChatUserReportedEvent` 를 발행한다
     (relationship 모듈이 카프카로 받아 `Report` 를 저장한다)
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현**
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): 읽음·나가기·삭제·신고`

---

### Task 7: REST API + Swagger

**Files:**
- Create: `.../api/ChatAPI.kt`, `.../api/ChatController.kt`, `.../api/request/*.kt`, `.../api/response/*.kt`
- Test: `.../api/ChatControllerTest.kt`

**Endpoints:**
| Method | Path | 설명 |
|---|---|---|
| POST | `/api/v1/chats/rooms` | 방 생성/조회 (body: partnerId) |
| GET | `/api/v1/chats/rooms` | 내 방 목록 (size, cursor) |
| GET | `/api/v1/chats/rooms/{roomId}/messages` | 메시지 목록 (size, cursor) |
| POST | `/api/v1/chats/rooms/{roomId}/messages` | 전송 (type, content, keys[]) |
| POST | `/api/v1/chats/rooms/{roomId}/read` | 읽음 |
| DELETE | `/api/v1/chats/rooms/{roomId}` | 나가기 |
| DELETE | `/api/v1/chats/messages/{messageId}` | 메시지 삭제 |
| POST | `/api/v1/chats/rooms/{roomId}/report` | 신고 |
| GET | `/api/v1/chats/upload-url` | 첨부 presign (filename, contentType) |

- [ ] **Step 1~5**: 컨트롤러 테스트(MockK) → 구현 → 통과 → 커밋 `feat(chat): REST API`

---

### Task 8: WebSocket 설정 + 실시간 통합 테스트

**Files:**
- Create: `.../config/ChatWebSocketConfiguration.kt`
- Test: `.../config/ChatWebSocketIntegrationTest.kt` (실제 STOMP 클라이언트 연결)

- [ ] **Step 1: 실패 테스트** — STOMP 클라이언트로 `/ws/chat` 연결 → `/topic/chat/room/{id}` 구독 → REST 로 전송 → **구독자가 메시지를 수신**하는지.
- [ ] **Step 2: 실패 확인**
- [ ] **Step 3: 구현** — `@EnableWebSocketMessageBroker`, `enableSimpleBroker("/topic")`, 엔드포인트 `/ws/chat`, JWT 핸드셰이크 인터셉터.
- [ ] **Step 4: 통과 확인**
- [ ] **Step 5: 커밋** — `feat(chat): WebSocket 실시간 수신`

---

### Task 9: i18n 키 + 최종 검증

- [ ] 신규 키를 12개 번들 전부에 등록: `chat.room.not-found`, `chat.room.forbidden`, `chat.self-room`, `chat.blocked`, `chat.message.not-found`, `chat.message.not-owner`, `chat.message.already-deleted`, `chat.message.empty`
- [ ] `./gradlew build` 전체 통과
- [ ] 커밋 — `feat(chat): i18n 키 등록`

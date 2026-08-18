# Langlez Backend — 현황과 로드맵

현재 상태와 남은 작업. 최종 갱신 2026-08-18.

관련 문서:
- `CLAUDE.md` — **코드 규약** (`module/member` 기준). 구 `CODE-CONVENTION.md` 를 여기로 옮겼다
- `docs/superpowers/plans/` — 단위 작업별 상세 계획서 (이력. 안의 `CODE-CONVENTION.md` 언급은 `CLAUDE.md` 로 읽는다)

구 `REVIEW.md`(확정 결함 7건)는 전부 조치돼 삭제했다. 조치 내역은 2절 끝에 요약해 뒀다.

---

## 1. 아키텍처 기준선

### 모듈 구조

```
core            프레임워크 없는 순수 계약(포트 + 이벤트 DTO)
common          웹·보안·예외·필터 공용
infra/rdb       JPA + QueryDSL + Outbox 베이스 + Flyway
infra/redis     Redisson, 캐시 포트 어댑터, 분산 락, pub/sub 브로드캐스터
infra/kafka     프로듀서·컨슈머 설정, DLT
module/*        도메인 모듈 (api / application / domain / infrastructure 4계층)
app/api         조립 + 실행
```

### 통신 규칙

| 목적 | 수단 |
|---|---|
| 모듈 간 상태 변경 전파 | **Kafka** (아웃박스 경유) |
| 응답을 기다려야 하는 조회 | **`core` 포트** (`BlockQuery`, `Storage`, `OnlineTracker`, `CacheProvider`, `Notificator`) |
| 접속 중인 사용자에게 실시간 전달 | **`MessageBroadcaster`** → Redis pub/sub → WebSocket |
| 고빈도 하트비트 | **Redis 직결** (Kafka 금지) |

아웃박스가 필요한 이유: 저장과 이벤트 발행이 한 트랜잭션에 묶여야 "저장은 됐는데 이벤트는 유실"이 원천 차단된다. 단, **가장 빈번한 쓰기(채팅 메시지)는 별도 아웃박스 행 대신 문서의 `published` 플래그**로 처리해 쓰기 증폭을 없앴다.

### 저장소 분담

| 데이터 | 저장소 | 이유 |
|---|---|---|
| 회원·프로필·방·참여자·아웃박스 | PostgreSQL | 조인·트랜잭션 필요, 유한 증가 |
| 채팅 메시지 본문 + 첨부 | MongoDB | 무한 증가, 첨부 임베드로 조회 1회 |
| 접속·화면 상태·분산 락·캐시·wave 채팅 | Redis | 휘발성·고빈도 |

### 스키마 관리

Flyway. `infra/rdb/src/main/resources/migration/V{n}__*.sql`.
운영·개발·테스트 모두 `ddl-auto: validate`. **이미 적용된 V 파일은 절대 수정하지 않는다** (체크섬 불일치로 기동 실패).

---

## 2. 완료된 것

### 인프라

- **Flyway 도입** — 엔티티에서 뽑은 DDL을 `V1__init.sql` 베이스라인으로. `ddl-auto`를 `update`/`none` → `validate` 로 통일. 통합테스트도 Flyway를 타므로 마이그레이션 자체가 검증된다
- **캐시 포트 이행** — Spring `@Cacheable`/`CacheManager` 전면 제거, `core.CacheProvider` 로 교체
- **Redis pub/sub 팬아웃** — `MessageBroadcaster` 포트 + `RedisMessageBroadcaster`. 인메모리 STOMP 브로커는 자기 JVM 세션에만 닿아 다중 인스턴스에서 조용히 깨진다
- **Lettuce 스택 제거** — Redisson만 사용. 쿼리 로거가 관측 대상 0건이었다

### 모듈

| 모듈 | 상태 |
|---|---|
| `member` | ✅ 기준 모듈. 2단계 캐시, 상태 머신, 접속 기록 배치 동기화 |
| `auth` | ✅ OAuth2, JWT, **1인 1기기** 정책, 쿠키 제거(모바일 전용) |
| `attachment` | ✅ presign → key 확정 흐름 |
| `profile` | ✅ 개인정보(성별·생일·국가)는 `member` 로 이관 |
| `chat` | ✅ 1:1 채팅 전체 (아래 상세) |
| `notification` | ✅ `chat-message-sent` 소비 → 3상태 판정 → 인앱/FCM |
| `relationship` | ✅ 팔로우·차단·신고 + `FollowQuery`/`BlockQuery` 구현 |
| `echo` | ✅ 글·타임라인·좋아요·댓글·해시태그·미디어 |
| `wave` | ✅ 음성방 + Redis 링버퍼 휘발성 채팅 |
| ~~`matching`~~ | ❌ 삭제 (의존 계층 소실, 재설계 필요) |
| ~~`interest`~~ | ❌ 삭제 (재설계 예정) |
| ~~`admin`~~ | ❌ 삭제 |

### chat 모듈 상세

방 생성·목록·메시지 조회·읽음·첨부(앨범)·나가기·삭제·신고 + WebSocket 실시간.

핵심 설계:
- 메시지는 **Mongo**, 첨부는 문서에 **임베드** → 목록 조회 1회
- 안 읽은 수는 `chat_room_members.unread_count` **비정규화** → 방 목록도 조회 1회
- 정렬·커서는 `created_at` 이 아니라 **방별 `seq`** (인스턴스 간 시계 차이로 순서가 뒤집힘)
- 나가기 = **재입장 정책** (나가도 이전 대화 전부 보임)
- 알림은 **발행 직전**에 "그 방 보는 중인지" 판정 → 보고 있으면 생략
- 이중 쓰기(Mongo→Postgres) 창은 **대사 스케줄러**가 5분마다 복구

### 코드 리뷰 확정 결함 조치 완료 (구 `REVIEW.md`)

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

---

## 3. 4개 모듈 재구축 완료 (2026-08-14)

`docs/superpowers/plans/2026-08-14-remaining-modules.md` 기준. 전부 병렬 구현 후 점검 완료.

- **A. notification** — `chat-message-sent` 수신 → 세 상태 판정(그 방 보는 중 / 앱만 켜짐 / 미접속) → 인앱 또는 FCM. `FcmPushSender` 는 Firebase Admin SDK 실사용, `fcm.credentials` 미설정 시 경고 로그만 남기고 무시
- **B. relationship** — 팔로우·차단·신고 API + `chat-user-reported` 수신 → `Report` 저장
- **C. echo** — 트위터형 피드 (글·타임라인·좋아요·댓글·해시태그·이미지)
- **D. wave** — 음성방 + **사라지는 채팅** (Redis 링버퍼만, 저장 안 함)

마이그레이션 번호 배정: wave=V5, relationship=V6, echo=V7.

**팔로우 그래프 연결:** `core.FollowQuery` 포트 신설로 결정. relationship 이 `FollowQueryImpl` 로 구현하고 echo 가 주입받는다. 이벤트 복제는 팔로우 그래프 사본을 echo 가 들고 있어야 해서 기각. 구현 주입이 없으면 `homeTimeline` 이 503 을 던지도록 명시적으로 실패시킨다.

**WebSocket 구독 인가:** wave 는 `WaveWebSocketConfiguration` 이 별도로 `sessions.isParticipant(roomId, memberId)` 를 검사한다. chat 설정 파일은 건드리지 않았다.

---

## 4. 남은 작업

구 `TODO.md` 를 이 절에 흡수했다(2026-08-18). 그 문서는 리팩토링 이전 기준이라 이미 끝난 항목이 미완으로 남아 있었고, 정책이 바뀌어 무효가 된 항목도 있었다. 아래 4.4 에 폐기 사유를 남긴다.

### 4.1 우선순위 높음

1. **`interest` 재설계** — 사용자가 직접 설계 예정. 2번의 선행 조건이다
2. **`matching` 재설계** — 매칭 알고리즘 입력(관심사·언어레벨·차단)을 먼저 정해야 한다
3. **정지/탈퇴 회원 접근 차단 필터** — `Member.requireActive()` 는 있고 로그인·토큰 갱신 경로에서만 부른다. 일반 API 경로는 정지된 회원도 그대로 통과한다. `JwtAuthenticationFilter` 뒤에 상태 검사를 붙일 자리
4. **`MemberWithdrawnEvent` + 탈퇴 시 토큰 전면 무효화** — 지금 `core/event/member` 에는 `MemberCreatedEvent`, `MemberHandleChangedEvent` 뿐이다. 탈퇴해도 발급된 액세스 토큰이 만료까지 살아 있고 리프레시 토큰도 안 지워진다. 탈퇴 이벤트 발행 → auth 가 리프레시 토큰 삭제 + 잔여 액세스 토큰 블랙리스트 등록

### 4.2 중간

5. **컨슈머 멱등성 하네스** — Kafka 는 at-least-once 다. 지금 컨슈머(`notification`, `relationship`)에 중복 수신 방어가 없어 재전달되면 알림이 두 번 가고 신고가 두 건 쌓인다. Redis `SETNX` 기반 `messageId` 중복 검사를 AOP 로
6. **Outbox history 아카이버** — `OutBoxHistoryProcessor` 가 완료 건을 `*_outbox_history` 로 옮기지만 그 히스토리 테이블을 비우는 쪽이 없다. 무한 증가한다. `@Scheduled` + `@DistributedLock` 배치 필요
7. **`TokenBlacklist` 정리** — `TokenRevoker` 로 개명, `remainingValiditySeconds` → `Duration` (코드에 TODO 있음)
8. **`ExceptionResponse` 포맷 확장** — 지금 `status` + `message` 뿐. `code`, `timestamp`, `path`, `traceId`(MDC) 추가 + 분산 추적용 TraceId 주입 필터
9. **`listRooms` 참여자 조회 최적화** — 페이지 크기만큼 단건 조회가 붙는다 (`ponytail:` 주석)
10. **대사 스케줄러 창 조정** — 활성 방 수만큼 Mongo 왕복 (`ponytail:` 주석)

### 4.3 낮음 / 정책 결정 필요

11. **리프레시 토큰 재사용 감지(RTR)** — 1인 1기기 정책이라 새 기기 로그인 시 기존 세션이 끊긴다(`auth.session-taken-over`). 탈취를 부분적으로만 막는다. 무효화된 리프레시 토큰으로 재발행을 시도하면 전 세션 강제 파기까지 갈지 결정 필요
12. **회원 검색 API** — handle 기반 페이징 검색. `MemberRepository.findAllByHandles` 는 있으나 부분 일치 검색 API 는 없다
13. **소셜 계정 추가 연동 / 연동 해제** — Google ↔ Apple 교차 연동. 현재는 가입 시 provider 하나에 고정
14. **마케팅 수신 동의 *일시*** — `agreedMarketingReceive`(Boolean) 만 있고 동의 시각이 없다. 법적으로 시각 기록이 필요한지 확인 후 `MemberAudit.agreedMarketingAt` 추가
15. **wave 채팅 신고 증거** — 휘발성이라 신고 시 스냅샷을 뜰지, 뜬다면 버퍼 보존 기간을 얼마로 할지
16. **`chat_messages` 시간 파티셔닝** — Mongo로 옮겨 당장은 불필요하나, Postgres에 남은 대용량 테이블이 생기면 재검토

### 4.4 정책 변경으로 폐기된 항목

되살리려면 정책부터 다시 논의해야 한다. 모르고 다시 착수하는 걸 막으려고 남긴다.

| 폐기 항목 | 사유 |
|---|---|
| 탈퇴 회원 개인정보 익명화 / 30일 유예 후 삭제 배치 | **의도적으로 하지 않는다.** 탈퇴 후 재가입해 같은 문제를 반복하는 회원을 추적해야 해서 계정 기록을 영구 보존한다 (`Member.withdraw` KDoc 참조) |
| 멀티 디바이스 세션 관리 / 기기 목록 / 원격 로그아웃 | **1인 1기기 정책**으로 확정. 기기 목록이라는 개념 자체가 없다. `MemberAudit.lastDeviceId` 하나로 끝난다 |
| Redis Stream DLQ (`autoClaim` 재시도 추적) | Redis Stream 을 안 쓴다. 메시징은 Kafka 로 통일했고 DLT 가 그 역할을 한다 |
| `Member.status` 라이프사이클 도입 | 완료 (`CREATED`/`ACTIVE`/`SUSPENDED`/`WITHDRAWN` + `suspend`/`unsuspend`/`withdraw`/`requireActive`) |
| `Member.profileImageUrl`, 약관 동의 이력, `getMe` 통합 조회 | 완료 (`Member.imageUrl`, `MemberAudit.agreedTermsAt`, `MemberMeResponse`) |
| Kafka 컨슈머 짝 맞추기 | 완료. `chat-message-sent` → notification, `chat-user-reported` → relationship |

---

## 5. 반복해서 터진 함정 (새 코드 작성 시 확인)

실제로 이 저장소에서 발생했던 것들. 전부 "조용히 잘못되는" 종류라 테스트 없이는 못 찾는다.

| 함정 | 증상 |
|---|---|
| 인증만 하고 인가 안 함 | 로그인한 아무나 남의 대화 구독. 와일드카드로 전체 흡입 |
| fail-open 검증 (`x != null &&` 조건) | 헤더를 빼기만 하면 검증 통째로 우회 |
| 클라이언트가 준 URL 저장 | 외부 주소 삽입, presigned 서명 노출 |
| 엔티티 읽고-쓰기로 카운터 증가 | 동시 요청 시 증가 유실 → DB 단일 UPDATE로 |
| `open val` 을 베이스 생성자에서 읽기 | 하위 클래스 값이 아직 0. `Semaphore(0)` 으로 전체 정지 |
| Redisson 코덱이 final 타입에 `@class` 미부여 | 캐시 read 100% 실패 → 무한 플래핑 |
| `Long` 을 Redis 셋에 저장 | `Integer` 로 돌아와 `contains(1L)` 이 조용히 false |
| `@Modifying` 쿼리에 트랜잭션 없음 | `No EntityManager with actual transaction` |
| i18n 키 누락 | 키 문자열이 그대로 사용자 응답에 노출 |
| 주석 안의 `/*` (예: `room/*`) | Kotlin 중첩 주석이 열려 뒤 코드가 통째로 주석 처리 |
| kotest `afterEach { clearMocks }` | `Then` 블록 사이에 돌아 `verify` 가 빈 기록을 본다 |

---

## 6. 검증 기준

```bash
./gradlew build          # 전체 빌드 + 테스트
```

- 통합테스트는 Testcontainers(Postgres · Redis · Mongo)를 띄운다. Docker 필요
- `ddl-auto: validate` 라 마이그레이션과 엔티티가 어긋나면 **기동 시점에** 잡힌다
- 신규 i18n 키는 `messages_*.properties` **12개 전부**에 있어야 한다

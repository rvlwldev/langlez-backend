# [B] 팔로우 이벤트 파이프라인 — 작업 보고

브랜치 `feat/follow-event-pipeline`, 커밋 5개, 푸시하지 않았다.

```
dc68d6f feat(notification): 팔로우 알림을 추가한다
51d62e6 feat(core): 카프카 컨슈머 멱등성을 추가한다
a04f759 feat(relationship): 아웃박스 발행·이력 스케줄러를 추가한다
e82b2a8 fix(rdb): 아웃박스 잠금 조회에 트랜잭션을 붙인다     <- 계획에 없던 것. 아래 참고
721fb33 refactor(relationship): MemberFollowedEvent 를 core 로 옮기고 followId 를 싣는다
```

---

## 계획에 없던 발견 — 아웃박스 발행은 애초에 전 모듈에서 깨져 있었다

`OutBoxRepository.fetch` 가 부르는 `@Lock(PESSIMISTIC_WRITE)` 파생 쿼리에 트랜잭션이 없었다.
Spring Data 의 기본 트랜잭션은 `SimpleJpaRepository` 가 **구현하는** CRUD 메서드에만 붙고
파생 쿼리 메서드에는 안 붙는다. 그래서 실행하면 매번:

```
org.springframework.dao.InvalidDataAccessApiUsageException:
Query requires transaction be in progress, but no transaction is known to be in progress
```

member·chat·relationship 아웃박스 **전부**가 이 경로를 탄다. 즉 스케줄러가 있는 member/chat 도
발행이 한 건도 안 되고 있었다. 기존 `OutBoxProcessorTest` 가 `repo` 를 통째로 목으로 대체해서
드러나지 않았다. `OutBoxRepository` 의 기존 주석("Spring Data 가 만든 조회 트랜잭션이 fetch 직후
커밋되면서 잠금이 즉시 풀린다")이 사실이 아니었다.

`@Transactional` 한 줄로 고쳤다 — 주석이 원래 의도한 동작(짧은 트랜잭션, fetch 직후 커밋,
중복 방지는 `@DistributedLock` 이 담당)이 그대로 성립한다.
이번에 추가한 `RelationshipOutBoxIntegrationTest` 가 이 회귀를 잡는다.

---

## 각 판단과 근거

### 1. 멱등 키 설계

**Kafka `messageId` 헤더는 오지 않는다.** 코드로 확인했다 —
`OutBoxProcessor` 는 `ProducerRecord(topic, key, payload)` 로만 발행하고
(`infra/rdb/.../OutBoxProcessor.kt:63`) 헤더를 붙이는 곳이 없다.
`DeadLetterPublishingRecoverer` 가 헤더를 붙이지만 그건 DLT 쪽이다.

그래서 키는 **`토픽 + 페이로드`의 SHA-256** 으로 잡았다.

- 전 토픽에 하나의 구현으로 붙는다. 리스너마다 키 추출 코드를 따로 두지 않는다.
- 페이로드 전문을 키로 쓰면 채팅 본문이 레디스에 그대로 남는다. 해시로 고정한다.
- 토픽 접두사는 같은 페이로드가 다른 토픽으로 흐를 때 서로 막지 않게 한다.

**전제: 페이로드에 그 발생 건을 유일하게 가리키는 값이 있어야 한다.**
`ChatMessageSentEvent` 는 `messageId`(Mongo ObjectId)가 이미 있다.
`MemberFollowedEvent(followerId, followedId)` 는 없었다 — **언팔로우 후 재팔로우가 이전과
완전히 같은 페이로드**라 재배달과 구분이 안 된다.

→ **`followId`(팔로우 행 id)를 이벤트에 추가**했다. 재팔로우는 새 행이라 id 가 새로 발급되고,
카프카 재배달은 같은 행이라 같은 값이다. UUID 를 새로 만들지 않은 이유: 행 id 는
결정적이고 도메인에서 이미 의미가 있어서, 같은 팔로우를 두 경로에서 발행해도 하나로 합쳐진다.

`RelationshipService.follow` 가 `repo.save(...)` 의 반환값에서 id 를 꺼낸다.
`RelationshipServiceTest` 는 relaxed 목이 돌려주는 `id = 0` 에 속지 않도록 `repo.save` 를 명시 스텁했다.

### 2. TTL = 1시간

- 가장 긴 재배달 창은 `KafkaConfiguration` 의 컨슈머 재시도(`maxElapsedTime = 300_000`, 5분)와
  리밸런싱(`max.poll.interval.ms` 기본 5분)이다. 1시간이면 그 12배라 배포 중 재시작이나
  짧은 장애 복구까지 덮는다.
- 더 늘려도 정상 이벤트를 막지는 않는다. 키에 `followId`/`messageId` 가 섞여 있어 같은 값이
  두 번 나오지 않기 때문이다. 순전히 레디스 메모리 문제라 1시간에서 끊었다.
- 컨슈머 그룹 오프셋을 처음부터 되감는 운영 리플레이는 어떤 TTL 로도 못 덮고, **덮어서도 안 된다.**

### 3. 레디스 장애 시 — fail-open (통과)

`isDuplicate` 가 실패하면 `false`(중복 아님)를 돌려주고 WARN 을 남긴다.

- 여기서 막으면 장애 시간 동안의 알림이 **통째로 사라진다.** 핸들러가 예외 없이 끝나
  오프셋이 커밋되므로 되살릴 방법이 없다.
- 통과의 최악은 알림이 두 번 뜨는 것, 즉 이 장치가 없던 지금 상태와 같다.
- 루트 `CLAUDE.md` 함정표의 "fail-open 검증"은 **인가·차단 같은 보안 판정**을 말한다.
  이건 사용자 경험 보호라 `module/CLAUDE.md` §3 의 "삼켜도 되는 실패"에 해당한다.
  코드 주석에 이 구분을 명시했다.
- `release` 도 실패를 삼킨다. 여기서 예외를 올리면 **원래 실패 원인을 덮어쓴다.**
  못 지워도 TTL 이 지나면 풀린다.

### 4. AOP 대신 명시 호출

- 어노테이션으로 감추면 갱신 시점이 코드에 안 보인다 — `@Cacheable` 을 안 쓰는 이유와 같다.
- **실패 시 표시를 되돌리는 처리**가 어드바이스 안으로 숨는다. 이게 가장 조용히 잘못될 부분이라
  호출부에 보이는 편이 낫다.
- 리스너가 셋뿐이라 감출 만큼 반복되지도 않는다.
- self-invocation 함정은 이 경우 문제가 아니다(카프카 컨테이너가 프록시된 빈을 호출한다).
  AOP 를 안 쓴 이유는 그게 아니라 위 두 가지다.

**표시는 처리 전에 남기고, 실패하면 되돌린다.** 처리 후에 표시하면 동시 재배달이 안 막히고,
표시만 하고 안 되돌리면 컨슈머 재시도와 DLT 재투입이 전부 "중복"으로 걸러져 메시지가 영영 사라진다.

### 5. `body` 는 빈 문자열

`Notification.body` 가 `nullable = false` 라 값이 필요하다.

- 채팅의 `event.preview` 에 해당하는 **동적 본문이 팔로우엔 없다.**
- 표시 문구("OO님이 회원님을 팔로우했습니다")는 클라이언트가 `data.followerId` 로 프로필을 붙여
  조립해야 한다 — 서버가 발신자 표시명을 조회하지 않는 것이 이 모듈의 규약이다.
- 그래서 정적 문장을 키로 하나 더 두면 **클라이언트가 안 쓰는 번역이 12개 늘 뿐이다.**

### 6. i18n 키

`TITLE_CHAT_MESSAGE = "notification.chat-message.title"` 이 **12개 번들에 실제로 들어 있다**
(ko: `새 메시지`). 같은 방식을 따라 `notification.member-followed` 를 12개 전부에 넣었다.
78 → 79키, 전 파일 동일. `messages_en`·`messages_id` 값은 ASCII 라 이스케이프가 필요 없었고,
나머지는 raw UTF-8. 로케일 접미사 없는 `messages.properties` 는 건드리지 않았다.

키 이름은 태스크가 준 문자열(`notification.member-followed`)을 그대로 썼다.
저장소 관례(`...title` 접미사)와는 어긋나지만 **클라이언트가 그리는 계약 문자열**이라
임의로 바꾸지 않았다.

### 7. `@Scheduled` + `@DistributedLock` 이 실제로 도는지

`@EnableRetry` 없는 `@Retryable` 과 같은 종류의 조용한 실패를 세 층으로 확인했다.

1. **`@EnableScheduling`** — `MainApplication` 에 있다(`app/api/.../MainApplication.kt:15`).
2. **어노테이션 자체** — `RelationshipOutBoxSchedulerTest` 가 리플렉션으로 `cron` 과
   `prefix` 값을 고정한다. 둘 중 하나만 붙는 조합(중복 발행 / 아무도 발행 안 함)을 막는다.
3. **AOP 프록시** — `RelationshipOutBoxIntegrationTest` 가 `AopUtils.isAopProxy(scheduler)` 를
   확인한다. `kotlin-spring` 플러그인이 `@Component` 를 안 열어주면 클래스가 final 이라
   프록시가 안 생기고 **락 없이 그냥 돈다.**

그리고 실제로 이 검증에서 위의 `@Transactional` 누락이 나왔다.

---

## `RelationshipService.report` 의 check-then-insert

**부분적으로만 덮인다. 이번에 고치지 않았다.**

- 덮이는 것: 같은 카프카 레코드의 재배달·동시 재배달. SETNX 가 원자적이라 하나만 통과한다.
- **안 덮이는 것 (셋 다 남아 있다):**
  1. 레디스 장애 시 fail-open 으로 두 건이 동시에 통과하면 `existsReport` → `save` 사이가
     그대로 경합한다.
  2. TTL(1시간)이 지난 뒤 같은 신고가 다시 들어오면 중복 검사를 통과한다.
  3. 클라이언트가 같은 신고를 두 번 올려 **서로 다른 두 레코드**가 생기면 페이로드가 같아야만
     걸린다. 필드가 하나라도 다르면(예: `reason` 오타 수정) 둘 다 통과한다.
- 근본 해결은 `reports` 에 `UNQ_REPORT(reporter_id, source_type, source_id, trigger_message_id)`
  유니크 제약이다. 다만 `trigger_message_id` 가 nullable 이라 Postgres 기본 동작에서 NULL 끼리는
  서로 다르게 취급된다 — `NULLS NOT DISTINCT`(PG15+) 를 쓰거나 부분 인덱스 두 개로 쪼개야 한다.
  범위 밖이라 남긴다.

---

## RED 확인 방법

구현을 일시적으로 되돌려(neuter) 새 테스트가 실제로 빨간불인 것을 눈으로 본 뒤 복원했다.
`--rerun-tasks --continue` 로 돌렸다.

| 되돌린 것 | 빨간불이 된 테스트 |
|---|---|
| `NotificationConsumer.once` 의 `isDuplicate` 가드 제거 | 채팅·팔로우 "같은 메시지가 두 번 배달되면 → 한 번만" 2건 |
| `NotificationConsumer` 의 `dedup.release` 제거 | "처리 도중 예외가 나면 → 재배달이 중복으로 안 걸린다" |
| `RelationshipConsumer` 의 `isDuplicate` 가드 제거 | "같은 이벤트가 다시 배달되면 → 서비스까지 안 간다" |
| `NotificationService.onMemberFollowed` 의 `title`/`data` 를 채팅 값으로 | 팔로우 알림 이력·FCM data 2건 |
| `RelationshipService` 가 `followId = 0L` 로 발행 | "정상 팔로우" + "재팔로우는 이벤트 값이 다르다" 2건 |
| `RelationshipOutBoxScheduler` 의 `@Scheduled`/`@DistributedLock` 제거 + `send()` 를 no-op | 어노테이션 2건 + 통합 테스트 "COMPLETE 로 바뀐다" |
| `RedisMessageDeduplicator` 가 TTL 없이 `setIfAbsent(MARK)` | "영구 키가 아니라 1시간 만료가 걸려 있다" |

첫 RED 실행 결과(요약): notification `25 tests completed, 4 failed`,
relationship `58 tests completed, 6 failed`, infra:redis TTL 1건 FAILED.

relaxed 목이 초록을 만드는 사고를 두 군데서 명시적으로 막았다.
- 컨슈머 테스트의 `MessageDeduplicator` 는 목이 아니라 **SETNX 를 흉내 내는 로컬 대역**이다.
  relaxed 목이면 `isDuplicate` 가 늘 `false` 라 중복 억제가 검증되지 않는다.
- `RelationshipServiceTest` 는 `repo.save` 를 명시 스텁했다. relaxed 목의 `Follow.id` 는 0이라
  `MemberFollowedEvent(0L, ...)` 를 기대해도 통과해버린다.

---

## 테스트 결과

```
./gradlew build --rerun-tasks
BUILD SUCCESSFUL in 2m 35s
tests=448 failures=0 errors=0 skipped=0     (main 기준 421 → +27)
```

`ProfileE2ETest` 는 **한 번에 통과했다.** 재실행이 필요 없었다.

새로 추가/수정한 스펙:
- `RedisMessageDeduplicatorTest` (infra:redis, testcontainers) — SETNX, followId 별 분리,
  토픽 분리, release, fail-open, TTL
- `RelationshipOutBoxSchedulerTest` — 어노테이션 고정
- `RelationshipOutBoxIntegrationTest` (testcontainers postgres+redis) — 팔로우 → 아웃박스 행 →
  발행 → COMPLETE, 발행 실패 시 tries 증가 + PENDING 유지 → 다음 주기 재시도, 이력 이관, AOP 프록시
- `NotificationConsumerTest` — 팔로우 리스너, 중복 억제, 재팔로우 재발송, 실패 시 release
- `NotificationServiceTest` — 팔로우 알림 온라인/오프라인 분기, title/body/type/data
- `RelationshipConsumerTest` — 중복 억제, 실패 시 release
- `RelationshipServiceTest`, `RelationshipEventListenerTest` — followId

## 범위 밖 (건드리지 않음)

`MemberUnfollowedEvent`, 비정규화 카운터, echo 팔로우 컨슈머 / 팔로우 그래프 캐시,
`AUDIT.md`·`README.md`, 리뷰 후속 N3/N4/N6.

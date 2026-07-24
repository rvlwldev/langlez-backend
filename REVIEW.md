# 매칭 큐 조건 필터 코드 리뷰

작업 브랜치: `rvlwldev/matching-age-lang-filter` (base: `refactoring`)
리뷰 범위: 실시간 매칭 큐에 **나이 + 언어 레벨 조건 필터** 추가 (성별 제외)
리뷰 방식: 정적 리뷰. 테스트는 워커가 26개 통과 보고(본 리뷰에서 재실행하지 않음).

## 변경 파일

| 파일 | 레이어 | 변경 |
|---|---|---|
| `domain/MatchingQueueFilter.kt` | domain | 신규 — 큐 필터 도메인 모델 |
| `api/MatchingRequest.kt` | api | `QueueFilter` DTO + `toDomain()` 추가 |
| `api/MatchingController.kt` | api | `POST /queue`에 선택적 필터 body 수신 |
| `domain/MatchingQueueRepository.kt` | domain | `saveFilter/findFilter/removeFilter` 추가 |
| `infrastructure/MatchingQueueRepositoryImpl.kt` | infra | Redis bucket + Jackson 직렬화 구현 |
| `application/MatchingService.kt` | application | 필터 저장/삭제 생명주기 + 양방향 사전 거르기 |
| `test/.../MatchingServiceTest.kt` | test | 유닛 테스트 추가 |
| `test/.../MatchingQueueIntegrationTest.kt` | test | 통합 테스트 추가 |

---

## 종합 평가

기능 설계는 요구사항대로 견고하다. 양방향 필터 검증, 필터 생명주기(join/leave/매칭성사 시 제거), 언어 레벨 하드 일치 + 미지정 시 기존 tolerance 유지 — 모두 의도대로 구현됐고 CAS 매칭 로직도 보존됐다.

다만 **성능(스케줄러 O(N²))과 입력 검증 공백**은 이번 변경 이전부터 존재하지만 이번 필터 추가로 악화되었으므로 짚어둔다. 아래는 심각도순.

---

## 심각도별 지적

### 🔴 High — 스케줄러의 O(N²) 반복 + 후보당 왕복 (성능)

`MatchingScheduler.rematchWaitingMembers()`는 5초마다 대기자 전원(`allMembers()`)에 대해 `attemptMatch`를 호출한다. 그리고 `attemptMatch`는 후보 각각에 대해:

- `isBlockedEitherWay`: 관계 조회 **DB 2회**
- `findProfile(candidateId)`: **DB 1회**
- `findFilter(candidateId)`: **Redis 1회** (이번 추가분)
- `findJoinedAt(candidateId)`: **Redis 1회**

대기자 N명, 후보 M명이면 5초마다 대략 `O(N × M)` 회의 DB/Redis 왕복이 발생한다(최악 N≈M이면 O(N²)). 이번 `findFilter` 추가가 후보당 왕복을 하나 더 늘렸다.

**개선 방향**
- `findProfile`를 후보 ID 리스트로 **배치 조회**(`findAllById`)해서 N+1 제거.
- 필터/joinedAt도 Redis `MGET`(Redisson `getBuckets`)으로 일괄 조회.
- 근본적으로는 스케줄러가 전원 재시도하는 대신, **참가/이탈 이벤트 기반**으로 해당 멤버만 재매칭하거나, score 버킷 단위로만 순회하도록 축소.
- 규모 작을 땐 현행 유지 가능하나, 대기자 수백 단위부터 체감 병목.

### 🟠 Medium — 입력 검증 부재 (신뢰 경계)

`QueueFilter`는 API request body로 그대로 들어오는데 `minAge`/`maxAge`에 대한 검증이 없다.

- `minAge > maxAge` (예: min=50, max=20) → 아무와도 매칭 안 됨(조용한 실패, 사용자는 원인 모름).
- 음수 나이, 비현실적 값(예: maxAge=500) → 무의미하게 통과.

`toDomain()` 또는 컨트롤러/DTO에서 `require(minAge == null || maxAge == null || minAge <= maxAge)` 및 음수 가드를 두는 것이 좋다. 신뢰 경계의 입력 검증은 생략하면 안 되는 항목.

### 🟠 Medium — 필터 읽기와 CAS 매칭 사이의 시점 차 (동시성)

후보의 `findFilter`/`findProfile`은 랭킹 단계에서 읽고, 실제 매칭은 그 뒤 CAS(`remove`)로 확정된다. 그 사이 후보가 이탈 후 다른 필터로 재참가하면 **낡은 필터/프로필로 매칭될 수 있다**.

CAS가 이중 매칭 자체는 막으므로 데이터 정합성 붕괴는 없고, "찰나의 낡은 조건 매칭"이라 실무상 영향은 미미하다. 다만 조건이 강한 제약(나이/언어)임을 감안하면, 최종 `connect` 직전에 확정된 두 사람의 필터를 한 번 더 검증하는 안전장치를 고려할 만하다. (현 단계에선 과할 수 있으니 기록만.)

### 🟡 Low — 필터 타입 이중화 + 데드코드 (코드 스멜 / 간결성)

- `MatchingRequest.QueueFilter`(DTO)와 `MatchingQueueFilter`(도메인)가 필드 3개로 사실상 동일. 프로젝트의 DTO/도메인 분리 컨벤션상 의도적일 수 있으나, `QueueFilter.isPresent()`는 **어디서도 호출되지 않는 데드코드**다(컨트롤러는 `toDomain()`만 사용, 서비스는 도메인 쪽 `isPresent()` 사용). 제거 권장.
- `RecommendationFilter`(gender 포함)와도 필드가 겹친다. 지금은 엔드포인트가 달라 분리 정당하지만, 공통 필드가 더 늘면 통합 여지 있음.

### 🟡 Low — joinQueue의 이중 조건 (간결성)

```kotlin
if (filter != null && filter.isPresent()) {
    queueRepository.saveFilter(memberId, filter)
}
```
`isPresent()`가 이미 "의미 있는 조건 존재"를 판정하므로 `filter?.takeIf { it.isPresent() }?.let { queueRepository.saveFilter(memberId, it) }`로 한 줄 축약 가능(선택). 현행도 충분히 읽힘.

### 🟡 Low — 직렬화 방식 혼재 (일관성)

같은 레포에서 `joinedAt`은 `Long`(epoch millis) bucket, `filter`는 JSON 문자열(Jackson) bucket으로 저장한다. 동작엔 문제없으나 저장 규약이 두 갈래다. 참고 사항.

### ⚪ Cosmetic — 포맷 노이즈

여러 파일 EOF에 빈 줄 추가, `MatchingController.joinQueue` 뒤 이중 공백, `MatchingServiceTest`의 wall-clock 주석 삭제. 기능 무관하나 ktlint에 걸릴 수 있음. 커밋 전 정리 권장.

---

## 오버엔지니어링 여부

전반적으로 **과설계 아님**. 필터를 별도 Redis bucket으로 두고 생명주기를 joinedAt과 동일 패턴으로 맞춘 것은 기존 코드와 자연스럽게 정합한다. 유일하게 덜어낼 부분은 위의 데드코드(`QueueFilter.isPresent()`)와 타입 이중화 정도.

---

## 더 나은 서비스를 위한 기능 제안

리뷰 범위를 넘지만, 매칭 서비스 완성도를 높일 아이디어를 우선순위순으로 제안한다.

1. **관심사 기반 매칭 강화 (핵심 방향)**
   현재 관심사는 자유 문자열 교집합 크기로만 랭킹에 반영된다. "등산 vs 하이킹" 같은 동의어를 하나로 보려면 `Interest` 마스터 테이블 + alias(별칭) 도입이 필요하다. 별도 `module/interest`로 분리하고, 서버 기동 시 시드 데이터를 멱등 적재(`ApplicationRunner` + `canonicalName` UNIQUE)하는 방식을 권장. 필터 로직은 String→ID 비교로 바뀌어 오히려 빨라진다.

2. **입력 검증 + 필터 확장**
   위 Medium 이슈 해결과 함께, 향후 성별/관심사 조건도 큐 필터에 추가할 수 있는 구조로 정리. `RecommendationFilter`와의 공통화도 이때 검토.

3. **매칭 성능 재설계 (규모 대비)**
   이벤트 기반 재매칭 또는 score 버킷 파티셔닝으로 스케줄러 O(N²) 제거. 성별을 큐 키로 분리(`matching:queue:{gender}`)하면 스캔 범위도 줄어든다.

4. **매칭 취소/타임아웃 UX**
   일정 시간(예: 60초) 내 매칭 실패 시 사용자에게 "조건을 완화하시겠어요?" 알림. tolerance가 이미 시간에 따라 확장되므로, 필터도 단계적으로 완화하는 옵션 제공 가능.

5. **매칭 이력 / 재매칭 방지**
   방금 매칭됐다 나온 상대와 즉시 재매칭되지 않도록 쿨다운(Redis TTL) 적용. 차단(block)은 이미 있으나, "최근 N분 내 매칭한 상대 제외"는 별개.

6. **관측성(Observability)**
   평균 매칭 대기시간, 큐 길이, 필터별 매칭 성공률 메트릭(Micrometer). 매칭 품질 튜닝의 근거가 된다.

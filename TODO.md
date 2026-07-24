# TODO

## 1. matching 모듈 N² 폴링 개선

`module/matching/src/main/kotlin/com/langlez/matching/application/MatchingScheduler.kt`가
5초마다 `queueRepository.allMembers()`로 대기열 전체를 읽어와 각 memberId에 대해
`matchingService.attemptMatch(memberId)`를 순차 호출한다. 대기자가 늘어날수록 매 사이클마다
쿼리/매칭 시도 횟수가 대기자 수에 비례해 폭증하는 구조(N² 성향).

개선 방향 (택1 또는 조합):
- 대기자 페이징/분할 처리로 한 사이클당 처리량 상한 설정
- 매칭 요청 발생 시점에 이벤트 드리븐으로 즉시 시도하고, 스케줄러는 보조/재시도 용도로만 축소
- ZSET score 기반으로 후보 범위를 좁혀서 `attemptMatch` 호출 자체를 줄이는 방식 검토

## 2. common/core 의존성 방향 정리

현재 `:common` 모듈이 `:core`를 참조하면서 동시에 `WebSecurityConfiguration`,
`JwtAuthenticationFilter` 등 Spring Web/Security 스택에 직접 결합되어 있어, "프레임워크
비의존 인터페이스"라는 `:core`의 원래 역할과 `:common`의 실제 책임(Spring 설정 전반)이
계층상 뒤섞여 있다.

개선 방향:
- `:core`는 순수 인터페이스/도메인 계약만 남기고, Spring 관련 구현체는 `:common` 또는
  각 기능 모듈 쪽으로 정리
- 모듈 간 의존 방향을 `core ← common ← module:*` 한 방향으로 명확히 정리
- 이 작업은 여러 모듈에 걸친 리팩토링이라 영향 범위가 크므로, 진행 전 별도로 범위/순서를
  먼저 정하고 시작할 것

---
(REVIEW.md의 3단계 "구조 개선" 항목 중 아직 처리하지 않은 것들. 나머지 항목은 이미 처리 완료.)

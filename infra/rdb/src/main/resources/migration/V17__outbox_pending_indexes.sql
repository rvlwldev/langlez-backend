-- 아웃박스 발행 대기 테이블 5개에 폴링 인덱스를 건다.
--
-- 지금까지 이 테이블들에는 PK 하나뿐이었다. V1/V8/V13 이 만든 아웃박스 인덱스는 전부
-- *_outbox_history(아카이브) 쪽이고, 정작 2초마다 돌아가는 발행 폴링은 아무것도 못 탔다.
--
--   OutBoxProcessor.send() -> OutBoxRepository.fetch()
--   select ... where status = 'PENDING' and tries <= ? order by created_at asc
--   limit ? for update skip locked
--
-- 아카이버가 매일 06:00 에만 돌아서(각 모듈의 *OutBoxHistoryScheduler) 하루치 COMPLETE/FAILED
-- 행이 발행 테이블에 그대로 쌓인다. payload 가 TEXT 라 행 폭이 KB 단위인데 그걸 초당 2회
-- (테이블 5개 * 0.5초 주기) 전량 Seq Scan + Sort 하고, FOR UPDATE SKIP LOCKED 라 훑은 행마다
-- 락을 잡았다 푸는 비용까지 붙는다. 낮 동안 선형으로 나빠지고 06:00 아카이브에 리셋되는 톱니 부하다.
--
-- ── 왜 (status, created_at) 복합이 아니라 부분 인덱스인가
-- 폴링이 보는 건 PENDING 뿐인데 전체 인덱스는 하루치 COMPLETE/FAILED 까지 담는다.
-- 실측(20만 행 중 PENDING 400): 부분 인덱스 32kB vs (status, created_at) 복합 7,952kB.
-- 읽는 행만 담으니 캐시에 상주하고, 아카이브 전후로 크기가 출렁이지도 않는다.
--
-- ── 왜 created_at 만 담고 tries 는 안 담는가
-- tries 는 발행에 실패한 행에서만 오르고 maxTries 를 넘으면 FAILED 로 빠져 부분 조건에서 사라진다.
-- 즉 PENDING 안에서 tries 로 걸러지는 행은 사실상 없어서, 인덱스에 넣어도 스캔량이 줄지 않는다.
-- created_at 만 담아 order by 를 인덱스가 그대로 제공하게 하는 편이 낫다(Sort 노드가 사라진다).
--
-- ── 부분 인덱스가 실제로 선택되려면 status 조건이 상수여야 한다
-- 플래너는 술어를 증명해야 부분 인덱스를 쓴다. status 가 바인드 파라미터로 나가면
-- `status = $1` 이 `status = 'PENDING'` 을 함의한다고 증명하지 못해 인덱스가 통째로 버려진다.
-- 다행히 이 쿼리는 파생 쿼리의 기본 인자(OutBoxRepository.fetch 가 넘기는 Status.PENDING)를
-- Hibernate 가 SQL 에 인라인한다 — 실행 로그가 `where status='PENDING' and tries<=3`,
-- params 는 빈 문자열이다(PerformanceLogger 로 확인). 이 조건을 파라미터로 바꾸면
-- 컴파일도 테스트도 통과한 채 인덱스만 조용히 안 타게 되니 주의한다.
--
-- concurrently 는 쓰지 않는다. 아직 운영 배포 전이라 잠글 트래픽이 없고, 무엇보다
-- CREATE INDEX CONCURRENTLY 는 기존 트랜잭션이 전부 끝나기를 기다려 통합테스트를 세운다(V8 참고).

create index IDX_MEMBER_EVENT_OUTBOX_PENDING
    on member_event_outbox (created_at)
 where status = 'PENDING';

create index IDX_CHAT_EVENT_OUTBOX_PENDING
    on chat_event_outbox (created_at)
 where status = 'PENDING';

create index IDX_ECHO_EVENT_OUTBOX_PENDING
    on echo_event_outbox (created_at)
 where status = 'PENDING';

create index IDX_FOLLOW_EVENT_OUTBOX_PENDING
    on follow_event_outbox (created_at)
 where status = 'PENDING';

create index IDX_BLOCK_EVENT_OUTBOX_PENDING
    on block_event_outbox (created_at)
 where status = 'PENDING';

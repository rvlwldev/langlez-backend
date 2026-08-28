-- *_outbox_history 보존 기간 정리 배치가 매일 "created_at < cutoff" 로 훑는다.
-- 인덱스가 없으면 그 배치가 테이블을 통째로 순회한다.
--
-- concurrently 는 쓰지 않는다. 규약(module/CLAUDE.md §6)이 "데이터가 있는 테이블에는
-- concurrently 를 검토하라"고 하지만, 여기서는 두 가지 이유로 일반 create index 가 맞다.
--
-- 1. 이 서비스는 아직 운영 배포 전이라(application-production.yml 이 플레이스홀더 상태)
--    이 테이블들에 잠글 트래픽이 없다. V6 도 같은 근거로 일반 create index 를 썼다.
-- 2. 더 중요한 이유 — CREATE INDEX CONCURRENTLY 는 **기존의 모든 트랜잭션이 끝나기를
--    기다린다.** 커넥션 풀이 스냅샷을 잡고 있으면 영원히 안 끝난다. 실제로 이 마이그레이션을
--    concurrently 로 썼을 때 통합테스트(Testcontainers)가 V8 진입 후 3시간 넘게 멈췄고,
--    Flyway 로그가 "Migrating ... [non-transactional]" 에서 한 줄도 더 진행되지 않았다.
--    운영에서도 장수 트랜잭션이 하나 있으면 같은 일이 난다.
--
-- 행이 크게 쌓인 뒤에 이런 인덱스를 걸어야 한다면, 정리 DELETE 를 먼저 배포해 테이블을 줄인 뒤
-- concurrently 를 별도 마이그레이션으로 분리해 돌린다. 그때도 열린 트랜잭션을 먼저 확인해야 한다.

create index IDX_MEMBER_OUTBOX_CREATED_AT
   on member_outbox_history (created_at);

create index IDX_RELATIONSHIP_OUTBOX_CREATED_AT
   on relationship_event_outbox_history (created_at);

create index IDX_CHAT_OUTBOX_CREATED_AT
   on chat_event_outbox_history (created_at);

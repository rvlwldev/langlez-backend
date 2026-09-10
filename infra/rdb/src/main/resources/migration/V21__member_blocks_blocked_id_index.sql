-- blockedAmong() 배치 차단 검사 시 blocked_id 선두 인덱스가 없어 풀 테이블 스캔이 발생하던 것을 방지한다 (C-05).
-- branch 1 (blocker_id = :viewer and blocked_id in :ids) 은 UNQ_MEMBER_BLOCK 을 타지만,
-- branch 2 (blocked_id = :viewer and blocker_id in :ids) 는 blocked_id 가 선두인 인덱스가 필요하다.
create index if not exists IDX_MEMBER_BLOCK_BLOCKED_BLOCKER
   on member_blocks (blocked_id, blocker_id);

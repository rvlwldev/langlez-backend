-- 정지 만료 배치(MemberSuspendHistoryRepository.findExpired)가 닫힌 이력이 누적되어도
-- 풀 테이블 스캔으로 빠지지 않도록 is_released = false 조건의 부분 인덱스를 둔다 (README §5.3-22).
create index if not exists IDX_MEMBER_SUSPEND_EXPIRED
    on member_suspend_history (release_at, id)
    where is_released = false;

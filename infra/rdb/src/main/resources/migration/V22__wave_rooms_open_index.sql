-- 진행 중인 음성방 목록 조회(findAllByEndedAtIsNullAndIdLessThanOrderByIdDesc)가
-- 종료된 방이 늘어나도 풀 테이블 스캔으로 빠지지 않도록 부분 인덱스를 둔다 (B-14).
create index if not exists IDX_WAVE_ROOMS_OPEN
   on wave_rooms (id desc)
   where ended_at is null;

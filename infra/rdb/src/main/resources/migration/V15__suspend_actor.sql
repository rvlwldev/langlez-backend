-- 누가 정지시켰는지 남긴다. 운영 조치는 감사 대상인데 member_suspend_history 에
-- 조치자를 담을 자리가 없었다 — 이력만 보고는 자동 조치인지 사람이 한 건지도 구분이 안 된다.
--
-- nullable 로 둔다. 이 컬럼이 생기기 전에 쌓인 행은 조치자 기록이 애초에 없어 백필할 값이 없고,
-- 아무 id 나 채워 넣으면 감사 기록으로서 오히려 해롭다. NULL 은 "알 수 없음"이다.
-- 앞으로 들어오는 행은 MemberWriter.suspend 가 인증에서 온 actorId 로 항상 채운다.
--
-- members(id) 를 가리키지만 물리적 FK 는 걸지 않는다. 다른 모듈 테이블을 참조하지 않는
-- 규약(member_profiles·notification_settings·member_languages 와 같다)을 따른다.
alter table member_suspend_history
    add column actor_id bigint;

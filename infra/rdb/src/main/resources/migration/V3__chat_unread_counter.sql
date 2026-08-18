-- V3: 안 읽은 수 카운터
--
-- 메시지 본문이 Mongo 로 가면서 안 읽은 수를 Postgres 조인으로 셀 수 없게 됐다.
-- 받는 쪽 참여자 행에 비정규화해 두면 방 목록이 여전히 쿼리 1회로 끝난다.
-- 기존 행은 0 으로 시작한다 — 이후 전송분부터 정확해지고, 읽음 처리 한 번이면 어차피 0 이다.
alter table chat_room_members
    add column unread_count bigint not null default 0;

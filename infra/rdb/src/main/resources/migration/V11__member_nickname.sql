-- 회원이 스스로 정하는 표시 이름. handle(고유 아이디)과 달리 유니크 제약이 없고,
-- 기존 회원은 정하지 않았으므로 not null 로 만들지 않는다(백필 금지 - 정책은 README/PR 설명 참고).
alter table members add column nickname varchar(20);

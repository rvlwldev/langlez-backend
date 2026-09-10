-- 회원 검색(handle, nickname 부분 일치)을 위한 pg_trgm + f_unaccent 함수 기반 GIN 인덱스를 생성한다.
-- V10__trgm_search_base.sql 의 f_unaccent() 와 pg_trgm 을 활용한다 (README §5.3-14).
create index if not exists IDX_MEMBERS_HANDLE_TRGM
    on members using gin (f_unaccent(handle) gin_trgm_ops);

create index if not exists IDX_MEMBERS_NICKNAME_TRGM
    on members using gin (f_unaccent(nickname) gin_trgm_ops);

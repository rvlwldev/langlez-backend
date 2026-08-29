-- 전 언어 부분일치 검색의 기반(확장 + 함수)만 만든다. 어떤 실제 테이블에도 인덱스를 걸지 않는다.
-- 컬럼 적용(GIN 인덱스 생성)은 다음 작업 몫이다. README §5 참고.
--
-- Postgres 기본 풀텍스트(tsvector/to_tsquery)를 안 쓰는 이유 (스파이크 실측):
--   1. pg_ts_config 에 ko/ja/zh_CN/zh_TW/vi 사전이 없다 - i18n 12개 언어 중 5개, 하필 CJK 전부 미지원
--   2. simple 사전으로 통일해도 조사가 막는다 - "공부" 0건, "공부를" 40,000건
--   3. 부분 문자열이 안 된다 - "apple" 이 든 글을 "pp" 로 검색하면 매칭 실패
--   4. 띄어쓰기 없는 한국어("한국어공부하실분구해요")가 통째로 토큰 하나가 돼 "한국어" 로 안 걸린다
-- pg_trgm 은 문자 3개씩 잘라 저장하므로 언어를 모른다. 12개 언어가 인덱스 하나를 공유한다.
--
-- create extension 은 슈퍼유저(또는 AWS RDS 의 rds_superuser) 권한이 필요하다.
-- 로컬·Testcontainers 는 admin 계정이 슈퍼유저라 조용히 통과하지만, 운영에서 Flyway 를
-- 최소 권한 계정으로 돌리면 "permission denied to create extension" 으로 여기서 죽고
-- 배포 전체가 중단된다. 운영 배포 전에 마스터 계정으로 두 확장을 미리 설치해 두거나
-- Flyway 실행 계정에 확장 설치 권한을 부여해야 한다 (README §5 참고).
create extension if not exists pg_trgm;

-- 사용자가 발음부호 없이 친다 (espanol -> español, francais -> français). CJK 는 건드리지 않는다.
create extension if not exists unaccent;

-- unaccent() 는 STABLE 이라 인덱스 식에 직접 쓰면
-- "ERROR: functions in index expression must be marked IMMUTABLE" 로 막힌다.
-- 사전을 regdictionary 로 고정해 넘기면 그 조합만 IMMUTABLE 로 선언할 수 있다.
-- 나중에 이 래퍼를 지우고 unaccent() 를 직접 쓰려다 막히는 걸 막기 위해 이유를 남긴다.
create or replace function f_unaccent(text) returns text as
  $$ select public.unaccent('public.unaccent'::regdictionary, $1) $$
  language sql immutable parallel safe strict;

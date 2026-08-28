-- 프로필 행을 만드는 코드가 지금까지 아예 없어서 member_profiles 는 비어 있었고,
-- 프로필 API 가 전부 404 였다. 앞으로 가입하는 회원은 member-created 컨슈머(ProfileConsumer)가 채우고,
-- 이미 있는 회원은 여기서 한 번 채운다.
--
-- visit_count 는 not null 이고 version 은 @Version 이라 0 으로 시작해야 한다.
-- bio/goal/want/mbti/language_level 은 nullable 이라 기본값을 넣지 않는다.
--
-- 인덱스를 만들지 않으므로 create index concurrently 도 필요 없다.
-- (concurrently 는 열린 트랜잭션이 끝나기를 무한정 기다려 통합테스트를 세운 전력이 있다.)
insert into member_profiles (id, visit_count, version)
select m.id, 0, 0
  from members m
 where not exists (select 1 from member_profiles p where p.id = m.id);

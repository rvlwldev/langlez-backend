-- 팔로우·차단 목록 조회가 인덱스를 못 타던 것을 고친다.
--
-- member_follows 에 있던 인덱스는 UNQ_MEMBER_FOLLOW(follower_id, followed_id) 하나뿐이었다.
-- 선두 컬럼이 follower_id 라 followed_id 단독 조회(팔로워 목록·팔로워 수)는 인덱스가 아예 없는
-- 것과 같아서 full scan 이었다.
--
-- follower_id 방향은 유니크 인덱스가 커버할 거라 보기 쉬운데 아니다. 그 인덱스엔 id 가 없어서
-- 정렬을 만들어주지 못하고, 플래너는 그럴 바엔 PK 를 역순으로 훑으며 follower_id 로 거르는 계획을
-- 고른다. LIMIT 만 보면 싸 보이지만 팔로잉이 적은 회원일수록 20건을 채우려 테이블을 더 멀리 훑는다.
-- (EXPLAIN 으로 확인했다 — FollowIndexIntegrationTest)
--
-- 커서 페이징이 id 내림차순(order by id desc, id < cursor)이므로 정렬까지 인덱스에 흡수시킨다.
-- 카운트(count where followed_id = ?)도 이 인덱스만으로 index-only scan 이 된다.
create index IDX_MEMBER_FOLLOW_FOLLOWED
   on member_follows (followed_id, id desc);

create index IDX_MEMBER_FOLLOW_FOLLOWER
   on member_follows (follower_id, id desc);

-- member_blocks 도 같은 문제였다. 차단 목록이 똑같은 커서 페이징이라 PK 역순 훑기로 빠진다.
-- 반면 양방향 차단 판정(isBlockedBetween)은 인자를 뒤집어 부를 뿐 두 컬럼 모두 등치라
-- 어느 방향이든 UNQ_MEMBER_BLOCK 선두 컬럼을 탄다. 그쪽엔 새 인덱스가 필요 없다.
create index IDX_MEMBER_BLOCK_BLOCKER
   on member_blocks (blocker_id, id desc);

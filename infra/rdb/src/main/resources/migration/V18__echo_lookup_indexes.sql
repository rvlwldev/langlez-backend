-- 피드(module/echo) 조회 인덱스. posts·comments·post_media 는 PK 외에 아무 인덱스도 없었고,
-- post_hashtags 는 UNQ_POST_HASHTAG(post_id, hashtag_id) 가 있었지만 선두 컬럼이 반대였다.
--
-- post_likes 는 손대지 않는다. findLikedPostIds 가 `post_id in (...) and member_id = ?` 라
-- UNQ_POST_LIKE(post_id, member_id) 의 선두 컬럼을 그대로 타고 index-only scan 이 된다.
--
-- concurrently 는 쓰지 않는다. 이유는 V8/V17 과 같다.

-- ── posts: 홈/회원 타임라인
--    author_id in (...) and deleted_at is null and blinded = false and id < cursor
--    order by id desc
--
-- V6(member_follows)와 똑같은 함정이었다. 인덱스가 PK 뿐이면 플래너는 LIMIT 만 보고
-- PK 를 역순으로 훑으며 author_id 로 거르는 계획을 고른다. 싸 보이지만 대상 회원이 글을 뜸하게
-- 쓸수록 20건을 채우려 테이블을 더 멀리 훑는다 — 30만 행에서 회원 타임라인 한 페이지를 뽑는 데
-- 99,974 행을 버리고 233ms 가 걸렸다. 인덱스를 걸면 같은 쿼리가 버리는 행 0, 1.3ms 다.
-- 커서 페이징이 id 내림차순이므로 id DESC 를 인덱스에 넣어 정렬까지 흡수시킨다.
--
-- 이 인덱스가 확실히 먹는 건 작성자가 하나인 회원 타임라인(EchoService.kt:106)이다.
-- 팔로잉 전체를 훑는 홈 타임라인(EchoService.kt:98)은 author_id in (...) 이라 btree 가
-- 전역 id 정렬을 못 만들어 준다(인덱스 출력 순서는 (author_id, id) 다). 그래서 플래너는
-- 데이터 분포에 따라 여전히 PK 역순 스캔을 고를 수 있고, 그건 오판이 아니라 두 계획을
-- 비교한 결과다 — 인덱스를 걸어 둔 덕에 비교할 대상이 생겼다는 게 이번 변경의 몫이다.
-- 홈 타임라인을 확실히 잡으려면 작성자별 lateral/union all 로 쿼리를 다시 써야 하는데,
-- 그건 인덱스가 아니라 쿼리 구조 문제라 이번 범위 밖이다.
--
-- soft delete 만 부분 조건에 넣고 blinded 는 넣지 않는다. QueryDSL 의 isNull 은 SQL 에
-- `deleted_at is null` 을 그대로 쓰지만 isFalse 는 바인드 파라미터로 나갈 수 있어, 부분 조건에
-- 넣으면 플래너가 함의를 증명하지 못하고 인덱스를 통째로 버릴 위험이 있다. 어차피 blinded 는
-- 신고 5건(Post.BLIND_THRESHOLD)을 넘긴 소수라 힙에서 걸러도 싸다.
create index IDX_POST_AUTHOR
    on posts (author_id, id desc)
 where deleted_at is null;

-- ── comments: 글 상세의 댓글 목록
--    post_id = ? and deleted_at is null and id > cursor order by id asc
-- 여기 커서는 오름차순이라 id 를 오름차순으로 담는다.
create index IDX_COMMENT_POST
    on comments (post_id, id)
 where deleted_at is null;

-- ── post_media: enrich 가 페이지마다 post_id in (...) 으로 부른다.
--    soft delete 컬럼이 없어 부분 인덱스로 만들 게 없다.
create index IDX_POST_MEDIA_POST
    on post_media (post_id);

-- ── post_hashtags: 해시태그 타임라인(findPostsByHashtag)이 hashtag_id 로 들어온다.
--    UNQ_POST_HASHTAG 는 (post_id, hashtag_id) 라 선두 컬럼이 없다. 단독 상수 조건이면
--    Postgres 가 인덱스 전체를 훑으며 후행 키로 걸러 주기라도 하지만, 실제 쿼리는 hashtags 와
--    조인해 들어와서 hashtag_id 값을 계획 시점에 모른다. 그래서 실측 계획은 Bitmap 도 아닌
--    post_hashtags 전체 Parallel Seq Scan 이었다. post_id 를 뒤에 붙여 조인 상대를 인덱스에서
--    바로 얻게 한다(index-only scan).
create index IDX_POST_HASHTAG_HASHTAG
    on post_hashtags (hashtag_id, post_id);

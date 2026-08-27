-- 같은 신고가 여러 행 쌓이는 것을 DB 로 막는다.
--
-- RelationshipService.report 는 existsReport 로 거른 뒤 저장하는 check-then-insert 라
-- 동시 요청·카프카 동시 재전달에 그대로 뚫린다. 앞단의 MessageDeduplicator 도 세 경우에 통과시킨다 —
-- 레디스 장애(판정 실패는 fail-open), TTL(1시간) 만료 후 재전달, 같은 신고인데 JSON 이 달라
-- 해시 키가 갈리는 경우. 그래서 이 인덱스가 최종 방어선이다.
--
-- 구성은 existsReport 의 조건과 **정확히 같다** — (신고자, 출처 종류, 출처 id, 트리거 메시지).
-- 앱이 통과시키는 것을 DB 가 거부하면 500 이 되므로 둘이 어긋나면 안 된다.
--
-- reported_user_id 는 일부러 넣지 않았다. ECHO_POST 는 source_id(글 id)가, CHAT_USER 는
-- source_id(방 id)와 신고자가 피신고자를 결정한다 (채팅방은 1:1 이다 — ChatService.partnerOrThrow).
-- 넣으면 오히려 약해진다: ECHO_POST 의 authorId 는 클라이언트 요청 본문에서 오는 값이라
-- 그것만 바꿔 같은 글을 몇 번이든 다시 신고할 수 있다.
--
-- 정당한 재신고는 막지 않는다. 다른 글·다른 방(source_id 가 다름), 채팅에서 다른 메시지를 집어
-- 신고(trigger_message_id 가 다름), 다른 사람이 같은 대상을 신고(reporter_id 가 다름)는 전부 통과한다.
-- 막히는 건 "같은 사람이 같은 지점을 다시 신고" 하나뿐이고, 그건 운영자가 같은 건을 두 번 처리하게 된다.

-- 인덱스를 만들기 전에 이미 쌓인 중복을 지운다. 한 쌍이라도 남아 있으면 인덱스 생성이 실패하고
-- ddl 검증 이전에 Flyway 단계에서 기동이 죽는다.
-- 가장 먼저 접수된 행(최소 id)을 남긴다 — 운영자가 이미 보고 있을 가능성이 높은 원본이고,
-- 뒤에 붙은 행은 재전달·재시도로 생긴 사본이다.
-- `is not distinct from` 을 쓰는 이유는 아래 nulls not distinct 와 같다. `=` 로 쓰면
-- trigger_message_id 가 NULL 인 중복(게시글 신고 전부가 여기 해당)이 하나도 안 지워진다.
delete from reports r
 using reports keep
 where r.reporter_id = keep.reporter_id
   and r.source_type = keep.source_type
   and r.source_id = keep.source_id
   and r.trigger_message_id is not distinct from keep.trigger_message_id
   and r.id > keep.id;

-- `nulls not distinct` 가 이 인덱스의 핵심이다. trigger_message_id 는 채팅 신고에서만 채워지고
-- Postgres 기본 동작(nulls distinct)에서는 NULL 끼리 서로 다른 값이라
-- (a, b, c, NULL) 을 몇 번이든 넣을 수 있다 — 게시글 신고는 전부 NULL 이라 제약이 통째로 무력해진다.
--
-- coalesce(trigger_message_id, '') 표현식 인덱스도 같은 구멍을 막지만 빈 문자열과 NULL 을
-- 한 값으로 합친다. ChatReportRequest.triggerMessageId 에 검증이 없어 클라이언트가 "" 를 보낼 수 있고,
-- 그러면 existsReport(`eq("")` vs `is null`)와 판정이 갈려 앱은 통과·DB 는 거부 = 500 이 된다.
-- 부분 인덱스 둘로 쪼개는 방법도 있지만 이름과 유지보수 지점이 둘로 늘 뿐 얻는 게 없다.
-- 로컬(docker/postgresql.yml)·테스트(Testcontainers)·운영 모두 postgres 16 이라 15+ 문법을 쓸 수 있다.
--
-- `concurrently` 는 쓰지 않는다. 이 서비스는 아직 운영 배포 전이라 reports 에 잠글 트래픽이 없고,
-- 무엇보다 위 정리 DELETE 와 인덱스 생성이 같은 트랜잭션에 있어야 그 사이에 들어온 중복 때문에
-- 생성이 실패하는 일이 없다. `concurrently` 는 트랜잭션 밖에서만 돌아 그 원자성을 포기해야 하고,
-- 실패 시 INVALID 인덱스를 남겨 수동 정리를 부른다. 행이 쌓인 뒤 같은 작업을 해야 한다면
-- 정리 DELETE 를 먼저 배포해 따로 돌린 뒤, 인덱스만 concurrently 로 거는 2단계로 나눈다.
create unique index UNQ_REPORT_IDENTITY
    on reports (reporter_id, source_type, source_id, trigger_message_id) nulls not distinct;

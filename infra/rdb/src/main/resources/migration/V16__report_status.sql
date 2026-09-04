-- 신고에 상태를 붙인다. 지금까지 reports 는 쌓이기만 하고 "확인 중인지 / 조치했는지"를
-- 담을 자리가 없어 운영자가 같은 건을 몇 번씩 다시 보게 돼 있었다.
--
-- `default 'RECEIVED'` 를 남긴다. 기존 행 백필용으로 필요한 건 이 alter 한 번뿐이지만,
-- 통합테스트(ReportDuplicateIntegrationTest)가 제약 자체를 찌르려고 reports 에 JDBC 로
-- 직접 insert 하면서 컬럼을 일부만 나열한다. 기본값을 빼면 그 insert 가 not null 위반으로
-- 죽는다. JPA 는 항상 값을 넣으므로 애플리케이션 경로에서는 기본값이 쓰이지 않고,
-- ddl-auto: validate 는 기본값을 보지 않아 엔티티와 어긋날 일도 없다.
alter table reports
    add column status varchar(20) not null default 'RECEIVED';

-- 운영자 메모. 사용자 응답에 나가지 않는다.
alter table reports
    add column admin_note text;

-- 마지막으로 상태를 바꾼 운영자와 그 시각. 처리 전에는 둘 다 NULL 이라 nullable 이다.
alter table reports
    add column handled_by bigint;

alter table reports
    add column handled_at timestamp(6) with time zone;

-- 운영 목록의 기본 질의가 "특정 상태를 최신순으로"다. 커서도 id 라 (status, id desc) 로 덮인다.
--
-- concurrently 를 쓰지 않는다. 아직 운영 배포 전이라 reports 에 잠글 트래픽이 없고,
-- concurrently 는 열려 있는 트랜잭션을 무한정 기다려 통합테스트를 세운 전력이 있다(V8).
create index IDX_REPORT_STATUS
    on reports (status, id desc);

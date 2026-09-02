-- 회원당 1행, 방해금지 시간대 전용. member_id 는 members(id) 를 가리키지만 물리적 FK 는 걸지 않는다.
-- 다른 모듈 테이블을 참조하지 않는 규약(module/profile.member_profiles 참고) 을 따른다.
create table notification_settings (
    member_id  bigint      primary key,
    quiet_from time,
    quiet_to   time,
    time_zone  varchar(64)
);

-- 끈 유형만 행으로 남는다. 행이 없으면 켜진 상태(기본 on) 다.
create table notification_mutes (
    member_id bigint      not null,
    type      varchar(50) not null,
    primary key (member_id, type)
);

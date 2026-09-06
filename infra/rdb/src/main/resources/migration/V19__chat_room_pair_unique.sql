-- V19: 1:1 방의 회원 쌍 유니크
--
-- ChatService.getOrCreateRoom 은 findRoomBetween -> createRoom 의 check-then-act 라
-- READ COMMITTED 아래에서 동시 요청이 둘 다 검사를 통과한다. 서로 동시에 채팅을 거는 경우뿐 아니라
-- 같은 사용자의 더블탭·재시도만으로도 난다. 그러면 방이 둘로 갈려 한 쪽은 방 1, 다른 쪽은 방 2에 쓰고
-- 서로의 메시지를 영영 못 본다. 갈린 뒤엔 자동 복구가 없으므로 DB 가 막아야 한다.
--
-- 회원 쌍을 chat_rooms 에 직접 둔다. 참여자 두 행(chat_room_members) 조인으로 방을 식별하던 방식은
-- 인자 순서에 무관하다는 장점이 있었지만 "쌍"에 유니크를 걸 자리가 없다 —
-- unq_chat_room_member(room_id, member_id) 는 같은 방에 같은 사람이 두 번 들어가는 것만 막는다.
-- 별도 pair 테이블로 빼는 방법도 있으나 참여자 행과 어긋날 지점만 하나 더 늘고 얻는 게 없다.
--
-- (a,b)/(b,a) 정규화를 빼먹으면 방이 두 개 생긴다는 것이 원래 이 설계를 피했던 이유다.
-- CHK_CHAT_ROOM_PAIR 가 그 걱정을 없앤다 — 정규화를 빠뜨린 INSERT 는 조용히 두 번째 방을 만드는 대신
-- 즉시 거부된다. 자기 자신과의 방(member_a = member_b)도 같은 체크가 막는다.
alter table chat_rooms
    add column member_a bigint,
    add column member_b bigint;

-- 기존 방은 참여자 두 행에서 채운다. 참여자가 정확히 둘이 아닌 방은 채워지지 않고
-- 바로 아래 set not null 에서 기동이 멈춘다 — 1:1 방으로 볼 수 없는 데이터라
-- 여기서 임의로 지우거나 만들어 내지 않고 운영이 보고 정하게 둔다.
update chat_rooms r
   set member_a = p.lo,
       member_b = p.hi
  from (
        select room_id, min(member_id) as lo, max(member_id) as hi
          from chat_room_members
         group by room_id
        having count(*) = 2 and min(member_id) <> max(member_id)
       ) p
 where p.room_id = r.id;

-- 이미 같은 쌍의 방이 둘 이상이면 유니크 추가가 실패한다. 일부러 병합하지 않는다 —
-- 메시지가 두 방에 갈려 있어 어느 쪽을 살릴지는 데이터를 봐야 정해지고 잘못 합치면 되돌릴 수 없다.
-- 실패한 채로 두고(기동 중단) 운영이 정리한 뒤 다시 올리는 편이 안전하다.
--
-- concurrently 를 쓰지 않는다. 이 서비스는 아직 배포 전이라 chat_rooms 에 잠글 트래픽이 없고,
-- 무엇보다 위 백필과 같은 트랜잭션에 있어야 그 사이에 들어온 방 때문에 실패하는 일이 없다.
-- (add constraint 는 애초에 트랜잭션 밖에서 도는 concurrently 와 함께 쓸 수 없다.)
alter table chat_rooms
    alter column member_a set not null,
    alter column member_b set not null,
    add constraint CHK_CHAT_ROOM_PAIR check (member_a < member_b),
    add constraint UNQ_CHAT_ROOM_PAIR unique (member_a, member_b);

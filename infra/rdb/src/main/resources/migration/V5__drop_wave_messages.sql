-- wave(음성방) 채팅은 사라지는 채팅이 됐다. 남은 죽은 테이블을 제거한다.
--
-- 방 안의 대화는 방이 끝나면 함께 끝난다. 저장소는 레디스 링버퍼뿐이고(방별 최근 N 개 + TTL)
-- 매핑하던 WaveMessage 엔티티도 지웠다.
-- ddl-auto=validate 는 매핑 없는 테이블을 문제 삼지 않아 그냥 두면 조용히 남는다.
-- 방 생명주기(wave_rooms)는 영속 정보라 그대로 둔다.

DROP TABLE IF EXISTS wave_messages;

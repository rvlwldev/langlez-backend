-- 메시지 본문이 MongoDB 로 옮겨가면서 남은 죽은 테이블을 제거한다.
--
-- V2 에서 만들었지만 이제 매핑하는 엔티티가 없다. 첨부는 Mongo 문서 안에 임베드되어
-- chat_message_files 도 함께 필요 없어졌다.
-- ddl-auto=validate 는 매핑 없는 테이블을 문제 삼지 않아 조용히 남아 있었다.

DROP TABLE IF EXISTS chat_message_files;
DROP TABLE IF EXISTS chat_messages;

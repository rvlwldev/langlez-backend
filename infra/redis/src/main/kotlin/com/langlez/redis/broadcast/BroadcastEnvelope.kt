package com.langlez.redis.broadcast

/**
 * 레디스 채널을 오가는 봉투.
 *
 * 채널을 STOMP 목적지마다 파지 않고 하나만 쓴다. 목적지 수만큼 구독을 늘리면
 * 방이 생길 때마다 전 인스턴스가 구독을 추가/해제해야 하고 그 동기화가 곧 결함이 된다.
 * 대신 목적지를 페이로드와 함께 실어 보내 수신 측이 어디로 밀지 판단하게 한다.
 *
 * 패키지가 `com.langlez.` 아래여야 한다 — 레디스 코덱이 이 접두사에만 타입 정보(@class)를
 * 남기고, 없으면 디코딩이 통째로 실패한다. (RedissonConfiguration.redisCodec 참고)
 */
data class BroadcastEnvelope(val topic: String, val payload: Any)

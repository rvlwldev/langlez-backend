package com.langlez.chat.domain

/**
 * 번호표 초기화 락을 waitTime 안에 못 잡았을 때.
 *
 * `IllegalStateException` 을 쓰면 안 된다 — 저장소 어댑터가 `@Repository` 라 Spring 예외 변환이
 * `InvalidDataAccessApiUsageException` 으로 바꿔 버리고, 그러면 application 의 catch 가 통째로 죽어
 * 503 변환이 조용히 사라진다. 변환기가 모르는 타입이어야 그대로 밖으로 나온다.
 */
class SeqLockTimeoutException(message: String) : RuntimeException(message)

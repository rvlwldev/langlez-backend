package com.langlez.redis.distributedLock

import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Target(FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    /**
     * 락 키의 접두사
     */
    val prefix: String = "lock:",
    /**
     * 락 만료 시간 (기본값: 30초)
     */
    val expirationTime: Long = 30,
    /**
     * 락 획득 재시도 간격 (기본값: 100ms)
     */
    val retryInterval: Long = 100,
    /**
     * 락 획득 재시도 횟수 (기본값: 30번)
     */
    val retryCount: Int = 30,
)

/**
 * 분산 락 키 구성을 위한 파라미터 어노테이션
 */
@Target(VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LockKey

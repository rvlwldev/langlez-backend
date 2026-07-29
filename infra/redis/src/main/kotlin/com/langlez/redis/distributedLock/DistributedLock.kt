package com.langlez.redis.distributedLock

import kotlin.annotation.AnnotationTarget.FUNCTION
import kotlin.annotation.AnnotationTarget.VALUE_PARAMETER
import kotlin.annotation.Retention
import kotlin.annotation.Target

@Target(FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val prefix: String = "lock:", // 락 키의 접두사
    val keys: Array<String> = [], // 락 키 생성 SpEL 표현식 배열
    val transactional: Boolean = false, // 락 획득 후 트랜잭션 시작 여부 (기본값: false)
    val leaseSecs: Long = 0, // 락 만료 시간 (0보다 작거나 같으면 자동 갱신)
    val waitMs: Long = 0, // 락 획득 재시도 간격
    val retries: Int = 0, // 락 획득 재시도 횟수
    val throwOnFailure: Boolean = false // 락 획득 실패 시 throw 여부
)

/** 분산 락 키 구성을 위한 파라미터 어노테이션 */
@Target(VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LockKey

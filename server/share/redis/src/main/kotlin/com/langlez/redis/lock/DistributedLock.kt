package com.langlez.redis.lock

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class DistributedLock(
    val prefix: String = "lock:",
    val keys: Array<String> = [],
    val transactional: Boolean = false,
    val leaseSecs: Long = 0,
    val waitMs: Long = 0,
    val retries: Int = 0,
    val throwOnFailure: Boolean = false,
)

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class LockKey

class LockAcquireFailedException(val key: String) : RuntimeException("락을 얻지 못했다: $key")

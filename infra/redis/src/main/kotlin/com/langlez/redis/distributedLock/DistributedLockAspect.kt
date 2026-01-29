package com.langlez.redis.distributedLock

import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.slf4j.LoggerFactory
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * 분산 락 AOP Aspect
 */
@Aspect
@Component
@Order(1)
class DistributedLockAspect(
    private val distributedLockHelper: DistributedLockHelper,
    private val redisLockService: RedisLockService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Pointcut("@annotation(com.langlez.redis.distributedLock.DistributedLock)")
    fun distributedLockPointcut() {
    }

    @Around("distributedLockPointcut() && @annotation(distributedLock)")
    @Transactional
    fun lock(
        joinPoint: ProceedingJoinPoint,
        distributedLock: DistributedLock,
    ): Any? {
        val lockKeys = distributedLockHelper.extractLockKeys(joinPoint)
        val lockName = distributedLockHelper.generateLockKey(distributedLock.prefix, lockKeys)

        log.debug("Attempting to acquire lock: $lockName")

        // 락 획득 시도 (재시도 로직 포함)
        val maxRetries = distributedLock.retryCount
        val retryInterval = distributedLock.retryInterval

        return try {
            var acquired = false
            for (attempt in 1..maxRetries) {
                if (redisLockService.acquireLock(lockName, distributedLock.expirationTime)) {
                    log.debug("Lock acquired successfully: $lockName (attempt $attempt)")
                    acquired = true
                    break
                }

                if (attempt < maxRetries) {
                    log.debug(
                        "Lock acquisition failed: $lockName (attempt $attempt), retrying in ${retryInterval}ms...",
                    )
                    Thread.sleep(retryInterval)
                }
            }

            if (!acquired) {
                log.error("Lock acquisition failed after $maxRetries attempts: $lockName")
                throw IllegalStateException("Lock acquisition failed for key: $lockName after $maxRetries attempts")
            }

            joinPoint.proceed()
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        } finally {
            // 락 해제
            try {
                redisLockService.releaseLock(lockName)
                log.debug("Lock released: $lockName")
            } catch (e: Exception) {
                log.warn("Failed to release lock: $lockName", e)
            }
        }
    }
}

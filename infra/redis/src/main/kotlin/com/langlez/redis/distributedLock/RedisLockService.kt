package com.langlez.redis.distributedLock

import java.util.concurrent.TimeUnit
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class RedisLockService(private val redissonClient: RedissonClient) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 분산 락을 사용하여 작업을 실행합니다.
     * @param key 락 키
     * @param waitTime 락 획득 대기 시간
     * @param leaseTime 락 점유 시간
     * @param unit 시간 단위
     * @param action 실행할 작업
     * @return 작업 결과
     * @throws IllegalStateException 락 획득 실패 시 발생
     */
    fun <T> executeWithLock(
        key: String,
        waitTime: Long,
        leaseTime: Long,
        unit: TimeUnit,
        throwOnFailure: Boolean = true,
        action: () -> T
    ): T? {
        val rLock = redissonClient.getLock(key)

        val acquired =
            try {
                rLock.tryLock(waitTime, leaseTime, unit)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw IllegalStateException("Interrupted while acquiring lock: $key", e)
            }

        if (!acquired) {
            if (throwOnFailure) {
                logger.error("Lock acquisition failed: $key")
                throw IllegalStateException("Lock acquisition failed for key: $key")
            } else {
                logger.debug("Lock already acquired by another node. Skipping execution: $key")
                return null
            }
        }

        logger.debug("Lock acquired successfully: $key")

        return try {
            action()
        } finally {
            try {
                if (rLock.isHeldByCurrentThread) {
                    rLock.unlock()
                    logger.debug("Lock released: $key")
                }
            } catch (e: Exception) {
                logger.warn("Failed to release lock: $key", e)
            }
        }
    }
}

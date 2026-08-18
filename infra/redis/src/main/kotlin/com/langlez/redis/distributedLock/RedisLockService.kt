package com.langlez.redis.distributedLock

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

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
     * @throws IllegalStateException 락 획득 실패 시 발생 (throwOnFailure = true 일 때)
     *
     * 주의: `throwOnFailure = false` 면 락 미획득 시 `null` 을 돌려준다.
     * 이 값이 non-null 반환 타입의 메서드로 흘러가면 호출부에서 NPE 가 난다.
     * 반환값을 쓰는 메서드에는 `throwOnFailure = true` 를 쓰거나 반환 타입을 nullable 로 둘 것.
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
                if (leaseTime <= 0) {
                    rLock.tryLock(waitTime, unit)
                } else {
                    rLock.tryLock(waitTime, leaseTime, unit)
                }
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

        val isReleased = AtomicBoolean(false)
        val releaseLock = {
            if (isReleased.compareAndSet(false, true)) {
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

        var isRegisteredToSync = false

        return try {
            val result = action()
            isRegisteredToSync = registerReleaseOnTxCompletion(releaseLock)
            result
        } catch (e: Throwable) {
            isRegisteredToSync = registerReleaseOnTxCompletion(releaseLock)
            throw e
        } finally {
            if (!isRegisteredToSync) {
                releaseLock()
            }
        }
    }

    private fun registerReleaseOnTxCompletion(releaseLock: () -> Unit): Boolean {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCompletion(status: Int) {
                    releaseLock()
                }
            })
            return true
        }
        return false
    }
}

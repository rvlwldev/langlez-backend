package com.langlez.redis.lock

import org.redisson.api.RedissonClient
import org.slf4j.LoggerFactory
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class RedisLockService(private val redissonClient: RedissonClient) {

    private val logger = LoggerFactory.getLogger(javaClass)

    fun <T> executeWithLock(
        key: String,
        waitTime: Long,
        leaseTime: Long,
        unit: TimeUnit,
        throwOnFailure: Boolean = true,
        action: () -> T,
    ): T? {
        val lock = redissonClient.getLock(key)

        val acquired = try {
            if (leaseTime <= 0) lock.tryLock(waitTime, unit) else lock.tryLock(waitTime, leaseTime, unit)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IllegalStateException("락을 기다리다 인터럽트됐다: $key", e)
        }

        if (!acquired) {
            if (throwOnFailure) throw LockAcquireFailedException(key)
            logger.debug("다른 노드가 락을 쥐고 있어 건너뛴다: {}", key)
            return null
        }

        val released = AtomicBoolean(false)
        val release = {
            if (released.compareAndSet(false, true)) {
                try {
                    if (lock.isHeldByCurrentThread) lock.unlock()
                } catch (e: Exception) {
                    logger.warn("락 해제에 실패했다: {}", key, e)
                }
            }
        }

        var deferred = false
        return try {
            val result = action()
            deferred = releaseAfterTransaction(release)
            result
        } catch (e: Throwable) {
            deferred = releaseAfterTransaction(release)
            throw e
        } finally {
            if (!deferred) release()
        }
    }

    private fun releaseAfterTransaction(release: () -> Unit): Boolean {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return false

        TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
            override fun afterCompletion(status: Int) = release()
        })
        return true
    }
}

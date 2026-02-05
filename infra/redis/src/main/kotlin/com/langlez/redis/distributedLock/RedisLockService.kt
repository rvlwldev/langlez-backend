package com.langlez.redis.distributedLock

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service

@Service
class RedisLockService(private val redis: RedisTemplate<String, String>) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 분산 락 획득 시도
     * @param key 락 키
     * @param ttl 락 만료 시간 (초)
     * @param block 락 획득 성공 시 실행할 블록
     * @return 락 획득 성공 여부
     */
    fun <T : Any> acquireLock(key: String, ttl: Long, block: () -> T): Boolean =
        if (acquireLock(key, ttl)) {
            try {
                block()
                true
            } finally {
                releaseLock(key)
            }
        } else {
            false
        }

    /**
     * 락 획득 시도
     * @param key 락 키
     * @param ttl 락 만료 시간 (초)
     * @return 락 획득 성공 여부
     */
    fun acquireLock(key: String, ttl: Long): Boolean {
        val script = DefaultRedisScript<Long>().apply {
            setScriptText(
                """
                if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
                    return 1
                else
                    return 0
                end """.trimIndent()
            )
            resultType = Long::class.java
        }

        val result = redis.execute(script, listOf(key), "locked", ttl.toString())
        val success = result == 1L

        if (success) logger.debug("Lock acquired successfully: $key")
        else logger.debug("Lock acquisition failed: $key")

        return success
    }

    /**
     * 락 해제
     * @param key 락 키
     */
    fun releaseLock(key: String) {
        try {
            val result = redis.delete(key)!!
            logger.debug("Lock released: $key")
        } catch (e: Exception) {
            logger.error("Failed to release lock: $key", e)
        }
    }

    /**
     * 락 존재 여부 확인
     * @param key 락 키
     */
    fun isLocked(key: String): Boolean = redis.hasKey(key) ?: false

    /**
     * 락 남은 시간 확인 (초 단위)
     * @param key 락 키
     */
    fun getLockTtl(key: String): Long? =
        try {
            redis.getExpire(key)
        } catch (e: Exception) {
            logger.error("Failed to get TTL for lock: $key", e)
            null
        }
}

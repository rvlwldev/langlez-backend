package com.langlez.redis.distributedLock

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service

@Service
class RedisLockService(
    private val redisTemplate: RedisTemplate<String, String>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 분산 락 획득 시도
     * @param lockKey 락 키
     * @param expirationSeconds 락 만료 시간 (초)
     * @param block 락 획득 성공 시 실행할 블록
     * @return 락 획득 성공 여부
     */
    fun <T : Any> acquireLock(
        lockKey: String,
        expirationSeconds: Long,
        block: () -> T,
    ): Boolean =
        if (acquireLock(lockKey, expirationSeconds)) {
            try {
                block()
                true
            } finally {
                releaseLock(lockKey)
            }
        } else {
            false
        }

    /**
     * 락 획득 시도
     * @param lockKey 락 키
     * @param expirationSeconds 락 만료 시간 (초)
     * @return 락 획득 성공 여부
     */
    fun acquireLock(
        lockKey: String,
        expirationSeconds: Long,
    ): Boolean {
        val script =
            """
            if redis.call('set', KEYS[1], ARGV[1], 'NX', 'EX', ARGV[2]) then
                return 1
            else
                return 0
            end
            """.trimIndent()

        val redisScript = DefaultRedisScript<Long>()
        redisScript.setScriptText(script)
        redisScript.setResultType(Long::class.java)

        val result =
            redisTemplate.execute(
                redisScript,
                listOf(lockKey),
                "locked",
                expirationSeconds.toString(),
            )

        val success = result == 1L
        if (success) {
            log.debug("Lock acquired successfully: $lockKey")
        } else {
            log.debug("Lock acquisition failed: $lockKey")
        }

        return success
    }

    /**
     * 락 해제
     */
    fun releaseLock(lockKey: String) {
        try {
            redisTemplate.delete(lockKey)
            log.debug("Lock released: $lockKey")
        } catch (e: Exception) {
            log.error("Failed to release lock: $lockKey", e)
        }
    }

    /**
     * 락 존재 여부 확인
     */
    fun isLocked(lockKey: String): Boolean = redisTemplate.hasKey(lockKey)

    /**
     * 락 남은 시간 확인 (초 단위)
     */
    fun getLockTtl(lockKey: String): Long? =
        try {
            redisTemplate.getExpire(lockKey)
        } catch (e: Exception) {
            log.error("Failed to get TTL for lock: $lockKey", e)
            null
        }
}

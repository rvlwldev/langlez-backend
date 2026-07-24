package com.langlez.redis.ratelimit

import org.redisson.api.RScript
import org.redisson.api.RedissonClient
import org.redisson.client.codec.StringCodec
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class DailyRateLimiter(private val redissonClient: RedissonClient) {

    private val incrAndExpireScript = """
        local count = redis.call('INCR', KEYS[1])
        if count == 1 then
            redis.call('EXPIRE', KEYS[1], ARGV[1])
        end
        return count
    """.trimIndent()

    /** key(예: "echo:post:123")에 대해 오늘 카운트를 1 증가시키고, limit 이하이면 true(허용), 초과하면 false(거부)를 반환한다. */
    fun tryConsume(key: String, limit: Int): Boolean {
        val redisKey = dailyKey(key)
        val ttlSeconds = Duration.ofHours(26).seconds
        val count: Long = redissonClient.getScript(StringCodec.INSTANCE).eval(
            RScript.Mode.READ_WRITE,
            incrAndExpireScript,
            RScript.ReturnType.INTEGER,
            listOf(redisKey),
            ttlSeconds.toString()
        )
        return count <= limit
    }

    fun currentCount(key: String): Long =
        redissonClient.getAtomicLong(dailyKey(key)).get()

    private fun dailyKey(key: String): String =
        "ratelimit:$key:${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}"
}


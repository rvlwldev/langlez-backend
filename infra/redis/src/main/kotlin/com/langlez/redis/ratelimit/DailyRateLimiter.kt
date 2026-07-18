package com.langlez.redis.ratelimit

import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
class DailyRateLimiter(private val redissonClient: RedissonClient) {

    /** key(예: "echo:post:123")에 대해 오늘 카운트를 1 증가시키고, limit 이하이면 true(허용), 초과하면 false(거부)를 반환한다. */
    fun tryConsume(key: String, limit: Int): Boolean {
        val redisKey = dailyKey(key)
        val counter = redissonClient.getAtomicLong(redisKey)
        val count = counter.incrementAndGet()
        if (count == 1L) {
            counter.expire(Duration.ofHours(26)) // 자정 경계 안전마진을 둔 TTL — 다음날엔 자연히 새 키로 시작
        }
        return count <= limit
    }

    fun currentCount(key: String): Long =
        redissonClient.getAtomicLong(dailyKey(key)).get()

    private fun dailyKey(key: String): String =
        "ratelimit:$key:${LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE)}"
}

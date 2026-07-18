package com.langlez.redis.security

import com.langlez.core.MemberPresenceTracker
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class MemberPresenceTrackerImpl(private val redissonClient: RedissonClient) : MemberPresenceTracker {

    override fun markOnline(memberId: Long) {
        val key = getRedisKey(memberId)
        val bucket = redissonClient.getBucket<String>(key)
        bucket.set("1", 30, TimeUnit.MINUTES)
    }

    override fun isOnline(memberId: Long): Boolean {
        val key = getRedisKey(memberId)
        return redissonClient.getBucket<String>(key).isExists
    }

    private fun getRedisKey(memberId: Long): String {
        return "presence:member:$memberId"
    }
}

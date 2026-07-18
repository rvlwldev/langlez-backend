package com.langlez.redis.security

import com.langlez.core.TokenBlacklist
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@Component
class TokenBlacklistImpl(private val redissonClient: RedissonClient) : TokenBlacklist {

    override fun blacklist(token: String, remainingValiditySeconds: Long) {
        if (remainingValiditySeconds <= 0) return
        val key = getRedisKey(token)
        val bucket = redissonClient.getBucket<String>(key)
        bucket.set("1", remainingValiditySeconds, TimeUnit.SECONDS)
    }

    override fun isBlacklisted(token: String): Boolean {
        val key = getRedisKey(token)
        return redissonClient.getBucket<String>(key).isExists
    }

    private fun getRedisKey(token: String): String {
        val sha256hex = hashSha256(token)
        return "blacklist:token:$sha256hex"
    }

    private fun hashSha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

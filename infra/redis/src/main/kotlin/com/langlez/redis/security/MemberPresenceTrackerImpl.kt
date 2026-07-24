package com.langlez.redis.security

import com.langlez.core.MemberPresenceTracker
import org.redisson.api.RedissonClient
import org.redisson.api.options.KeysScanOptions
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class MemberPresenceTrackerImpl(private val redissonClient: RedissonClient) : MemberPresenceTracker {

    override fun markOnline(memberId: Long) {
        val key = getRedisKey(memberId)
        val bucket = redissonClient.getBucket<String>(key)
        bucket.set("1", Duration.ofMinutes(30))
    }

    override fun isOnline(memberId: Long): Boolean {
        val key = getRedisKey(memberId)
        return redissonClient.getBucket<String>(key).isExists
    }

    override fun countOnline(): Long {
        val options = KeysScanOptions.defaults().pattern("presence:member:*").chunkSize(100)
        return redissonClient.keys.getKeysStream(options).count()
    }

    override fun areOnline(memberIds: Collection<Long>): Map<Long, Boolean> {
        if (memberIds.isEmpty()) return emptyMap()
        val keyToId = memberIds.associateBy { getRedisKey(it) }
        val buckets = redissonClient.getBuckets().get<String>(*keyToId.keys.toTypedArray())
        return keyToId.entries.associate { (key, id) ->
            id to (buckets[key] != null)
        }
    }

    private fun getRedisKey(memberId: Long): String {
        return "presence:member:$memberId"
    }
}


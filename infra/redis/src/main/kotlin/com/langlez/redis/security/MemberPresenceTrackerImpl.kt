package com.langlez.redis.security

import com.langlez.core.MemberPresenceTracker
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class MemberPresenceTrackerImpl(private val redissonClient: RedissonClient) : MemberPresenceTracker {

    private val onlineSetKey = "presence:online_members"

    override fun markOnline(memberId: Long) {
        val key = getRedisKey(memberId)
        val bucket = redissonClient.getBucket<String>(key)
        bucket.set("1", Duration.ofMinutes(30))

        val onlineSet = redissonClient.getScoredSortedSet<Long>(onlineSetKey)
        onlineSet.add(System.currentTimeMillis().toDouble(), memberId)
    }

    override fun isOnline(memberId: Long): Boolean {
        val key = getRedisKey(memberId)
        return redissonClient.getBucket<String>(key).isExists
    }

    override fun countOnline(): Long {
        val onlineSet = redissonClient.getScoredSortedSet<Long>(onlineSetKey)
        val cutoff = (System.currentTimeMillis() - Duration.ofMinutes(30).toMillis()).toDouble()
        onlineSet.removeRangeByScore(0.0, true, cutoff, false)
        return onlineSet.size().toLong()
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



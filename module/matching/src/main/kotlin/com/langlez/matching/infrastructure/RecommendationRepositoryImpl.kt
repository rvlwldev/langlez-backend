package com.langlez.matching.infrastructure

import com.langlez.matching.domain.RecommendationRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Duration

@Repository
class RecommendationRepositoryImpl(
    private val redissonClient: RedissonClient,
) : RecommendationRepository {

    override fun save(memberId: Long, usernames: List<String>, ttl: Duration) {
        redissonClient.getBucket<List<String>>(key(memberId)).set(usernames, ttl)
    }

    override fun find(memberId: Long): List<String>? =
        redissonClient.getBucket<List<String>>(key(memberId)).get()

    private fun key(memberId: Long) = "matching:recommend:$memberId"
}

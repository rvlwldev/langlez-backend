package com.langlez.echo.infrastructure

import com.langlez.echo.domain.HashtagDailyCount
import com.langlez.echo.domain.HashtagTrendCount
import com.langlez.echo.domain.HashtagTrendRepository
import org.redisson.api.RedissonClient
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Repository
class HashtagTrendRepositoryImpl(
    private val redisson: RedissonClient
) : HashtagTrendRepository {

    override fun recordPostUsage(hashtag: String) {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val key = "echo:hashtag:post:$dateStr"
        val set = redisson.getScoredSortedSet<String>(key)
        set.addScore(hashtag, 1.0)
        set.expire(Duration.ofDays(31))
    }

    override fun recordSearch(hashtag: String) {
        val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val key = "echo:hashtag:search:$dateStr"
        val set = redisson.getScoredSortedSet<String>(key)
        set.addScore(hashtag, 1.0)
        set.expire(Duration.ofDays(31))
    }

    override fun getTrending(days: Int, limit: Int): List<HashtagTrendCount> {
        val today = LocalDate.now()
        val scores = mutableMapOf<String, Double>()
        for (i in 0 until days) {
            val dateStr = today.minusDays(i.toLong()).format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            val key = "echo:hashtag:post:$dateStr"
            val set = redisson.getScoredSortedSet<String>(key)
            val entries = set.entryRange(0, -1)
            for (entry in entries) {
                scores[entry.value] = (scores[entry.value] ?: 0.0) + entry.score
            }
        }
        return scores.entries.sortedByDescending { it.value }
            .take(limit)
            .map { HashtagTrendCount(it.key, it.value.toLong()) }
    }

    override fun snapshotDailyCounts(date: LocalDate): List<HashtagDailyCount> {
        val dateStr = date.format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val postKey = "echo:hashtag:post:$dateStr"
        val searchKey = "echo:hashtag:search:$dateStr"

        val postSet = redisson.getScoredSortedSet<String>(postKey)
        val searchSet = redisson.getScoredSortedSet<String>(searchKey)

        val postEntries = postSet.entryRange(0, -1)
        val searchEntries = searchSet.entryRange(0, -1)

        val postMap = postEntries.associate { it.value to it.score.toLong() }
        val searchMap = searchEntries.associate { it.value to it.score.toLong() }

        val allHashtags = postMap.keys + searchMap.keys
        return allHashtags.map { tag ->
            HashtagDailyCount(
                hashtag = tag,
                postCount = postMap[tag] ?: 0L,
                searchCount = searchMap[tag] ?: 0L
            )
        }
    }
}

package com.langlez.echo.domain

import java.time.LocalDate

interface HashtagTrendRepository {
    fun recordPostUsage(hashtag: String)
    fun recordSearch(hashtag: String)
    fun getTrending(days: Int, limit: Int): List<HashtagTrendCount>
    fun snapshotDailyCounts(date: LocalDate): List<HashtagDailyCount>
}

data class HashtagTrendCount(val hashtag: String, val count: Long)
data class HashtagDailyCount(val hashtag: String, val postCount: Long, val searchCount: Long)

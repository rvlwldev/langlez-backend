package com.langlez.echo.domain

import java.time.LocalDate

interface HashtagDailyStatRepository {
    fun save(stat: HashtagDailyStat): HashtagDailyStat
    fun saveAll(stats: List<HashtagDailyStat>): List<HashtagDailyStat>
    fun findByHashtagAndStatDate(hashtag: String, statDate: LocalDate): HashtagDailyStat?
    fun findAllByStatDateAndHashtagIn(statDate: LocalDate, hashtags: Collection<String>): List<HashtagDailyStat>
}

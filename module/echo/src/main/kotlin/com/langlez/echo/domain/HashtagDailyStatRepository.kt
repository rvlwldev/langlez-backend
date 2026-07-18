package com.langlez.echo.domain

import java.time.LocalDate

interface HashtagDailyStatRepository {
    fun save(stat: HashtagDailyStat): HashtagDailyStat
    fun findByHashtagAndStatDate(hashtag: String, statDate: LocalDate): HashtagDailyStat?
}

package com.langlez.echo.infrastructure

import com.langlez.echo.domain.HashtagDailyStat
import com.langlez.echo.domain.HashtagDailyStatRepository
import com.langlez.echo.infrastructure.jpa.HashtagDailyStatJpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

@Repository
class HashtagDailyStatRepositoryImpl(
    private val jpa: HashtagDailyStatJpaRepository
) : HashtagDailyStatRepository {
    override fun save(stat: HashtagDailyStat): HashtagDailyStat = jpa.save(stat)
    override fun saveAll(stats: List<HashtagDailyStat>): List<HashtagDailyStat> = jpa.saveAll(stats)
    override fun findByHashtagAndStatDate(hashtag: String, statDate: LocalDate): HashtagDailyStat? =
        jpa.findByHashtagAndStatDate(hashtag, statDate)
    override fun findAllByStatDateAndHashtagIn(statDate: LocalDate, hashtags: Collection<String>): List<HashtagDailyStat> =
        if (hashtags.isEmpty()) emptyList() else jpa.findAllByStatDateAndHashtagIn(statDate, hashtags)
}

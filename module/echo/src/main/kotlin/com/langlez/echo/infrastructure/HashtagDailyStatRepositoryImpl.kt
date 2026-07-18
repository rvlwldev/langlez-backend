package com.langlez.echo.infrastructure

import com.langlez.echo.domain.HashtagDailyStat
import com.langlez.echo.domain.HashtagDailyStatRepository
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import java.time.LocalDate

interface HashtagDailyStatJpaRepository : JpaRepository<HashtagDailyStat, Long> {
    fun findByHashtagAndStatDate(hashtag: String, statDate: LocalDate): HashtagDailyStat?
}

@Repository
class HashtagDailyStatRepositoryImpl(
    private val jpa: HashtagDailyStatJpaRepository
) : HashtagDailyStatRepository {
    override fun save(stat: HashtagDailyStat): HashtagDailyStat = jpa.save(stat)
    override fun findByHashtagAndStatDate(hashtag: String, statDate: LocalDate): HashtagDailyStat? =
        jpa.findByHashtagAndStatDate(hashtag, statDate)
}

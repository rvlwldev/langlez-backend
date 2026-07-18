package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.HashtagDailyStat
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface HashtagDailyStatJpaRepository : JpaRepository<HashtagDailyStat, Long> {
    fun findByHashtagAndStatDate(hashtag: String, statDate: LocalDate): HashtagDailyStat?
}

package com.langlez.echo.application

import com.langlez.echo.domain.HashtagDailyStat
import com.langlez.echo.domain.HashtagDailyStatRepository
import com.langlez.echo.domain.HashtagTrendRepository
import com.langlez.redis.distributedLock.DistributedLock
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.support.TransactionTemplate
import java.time.LocalDate

@Component
class HashtagStatSyncScheduler(
    private val hashtagTrendRepository: HashtagTrendRepository,
    private val hashtagDailyStatRepository: HashtagDailyStatRepository,
    private val transactionTemplate: TransactionTemplate,
) {

    @Scheduled(fixedDelay = 3600000)
    @DistributedLock(prefix = "lock:echo-hashtag-stat-sync", ttl = 10, wait = 0, retries = 0, throwOnFailure = false)
    fun syncHashtagStats() {
        val today = LocalDate.now()
        val dailyCounts = hashtagTrendRepository.snapshotDailyCounts(today)
        if (dailyCounts.isEmpty()) return

        transactionTemplate.execute {
            dailyCounts.forEach { count ->
                val stat = hashtagDailyStatRepository.findByHashtagAndStatDate(count.hashtag, today)
                if (stat != null) {
                    stat.postCount = count.postCount
                    stat.searchCount = count.searchCount
                    hashtagDailyStatRepository.save(stat)
                } else {
                    hashtagDailyStatRepository.save(
                        HashtagDailyStat(
                            hashtag = count.hashtag,
                            statDate = today,
                            postCount = count.postCount,
                            searchCount = count.searchCount
                        )
                    )
                }
            }
        }
    }
}

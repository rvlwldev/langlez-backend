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
            val hashtags = dailyCounts.map { it.hashtag }
            val existingMap = hashtagDailyStatRepository.findAllByStatDateAndHashtagIn(today, hashtags)
                .associateBy { it.hashtag }

            val toSave = dailyCounts.map { count ->
                val stat = existingMap[count.hashtag]
                if (stat != null) {
                    stat.postCount = count.postCount
                    stat.searchCount = count.searchCount
                    stat
                } else {
                    HashtagDailyStat(
                        hashtag = count.hashtag,
                        statDate = today,
                        postCount = count.postCount,
                        searchCount = count.searchCount
                    )
                }
            }
            hashtagDailyStatRepository.saveAll(toSave)
        }
    }
}

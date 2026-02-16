package com.langlez.member.application

import com.langlez.file.application.FileStorage
import com.langlez.member.domain.MemberRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class MemberScheduler(private val repo: MemberRepository, private val storage: FileStorage) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @Scheduled(fixedRate = 30 * 60 * 1000)
    @Transactional
    fun cleanupIncompleteRegistrations() = runBlocking {
        val threshold = Instant.now().minus(2, ChronoUnit.HOURS)
        val incompleteMembers = repo.findAllIncompleteOlderThan(threshold)

        if (incompleteMembers.isNotEmpty()) {
            // S3 이미지 병렬 정리
            incompleteMembers.flatMap { it.images }.map { image ->
                async {
                    runCatching { storage.delete(image.url) }
                        .onFailure { logger.warn("Failed to delete image {}", image.url, it) }
                }
            }.awaitAll()

            repo.deleteAll(incompleteMembers)
            logger.info("Cleaned up {} incomplete registrations older than 2 hours", incompleteMembers.size)
        }
    }
}


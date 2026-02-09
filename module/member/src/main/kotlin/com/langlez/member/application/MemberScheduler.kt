package com.langlez.member.application

import com.langlez.member.domain.MemberRepository
import java.time.Instant
import java.time.temporal.ChronoUnit
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

/**
 * Member 스케줄러
 * - 2시간 내 초기화 미완료 계정 자동 삭제
 */
@Component
class MemberScheduler(private val repo: MemberRepository) {

    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * 30분마다 실행: 생성 후 2시간이 지났는데 init = false 인 계정 삭제
     * - 엔티티 조회 후 삭제하여 @ElementCollection, @OneToMany cascade 정상 작동
     */
    @Scheduled(fixedRate = 30 * 60 * 1000) // 30분
    @Transactional
    fun cleanupIncompleteRegistrations() {
        val threshold = Instant.now().minus(2, ChronoUnit.HOURS)
        val incompleteMembers = repo.findIncompleteOlderThan(threshold)

        if (incompleteMembers.isNotEmpty()) {
            repo.deleteAll(incompleteMembers)
            log.info(
                    "Cleaned up {} incomplete registrations older than 2 hours",
                    incompleteMembers.size
            )
        }
    }
}

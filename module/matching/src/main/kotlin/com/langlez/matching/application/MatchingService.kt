package com.langlez.matching.application

import com.langlez.chat.application.ChatService
import com.langlez.core.LanglezException
import com.langlez.matching.api.MatchingResponse
import com.langlez.matching.domain.MatchingQueueFilter
import com.langlez.matching.domain.MatchingQueueRepository
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileRepository
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDate
import java.time.Period

/**
 * PLAN.md Phase 5 실시간 매칭 큐.
 *
 * 큐 참가/이탈 및 "매칭 시도" 로직(참가 직후 1회, 그리고 [MatchingScheduler]에서 주기적으로 재사용)을 담당한다.
 */
@Service
@Transactional
class MatchingService(
    private val queueRepository: MatchingQueueRepository,
    private val profileRepository: ProfileRepository,
    private val memberRepository: MemberRepository,
    private val relationshipRepository: RelationshipRepository,
    private val chatService: ChatService,
    private val matchBroadcaster: MatchBroadcaster,
) {

    fun joinQueue(memberId: Long, filter: MatchingQueueFilter? = null): MatchingResponse.QueueStatus {
        if (queueRepository.isQueued(memberId)) {
            throw LanglezException(409, "matching.already-queued")
        }

        val profile = profileRepository.findProfile(memberId)
            ?: throw LanglezException(404, "profile.not-found")
        val languageLevel = profile.languageLevel
            ?: throw LanglezException(400, "matching.language-level-required")

        queueRepository.add(memberId, MatchingScoreCalculator.baseScore(languageLevel))
        queueRepository.saveJoinedAt(memberId, Instant.now())
        if (filter != null && filter.isPresent()) {
            queueRepository.saveFilter(memberId, filter)
        }

        return attemptMatch(memberId) ?: MatchingResponse.QueueStatus(MatchingResponse.QueueStatus.Status.WAITING)
    }

    fun leaveQueue(memberId: Long) {
        queueRepository.remove(memberId)
        queueRepository.removeJoinedAt(memberId)
        queueRepository.removeFilter(memberId)
    }

    /**
     * 큐 참가 시점과 스케줄러에서 공용으로 재사용하는 매칭 시도 로직.
     * memberId가 이미 큐에 없거나(다른 스레드가 먼저 매칭시켰거나) 적당한 후보가 없으면 null을 반환한다(대기 유지).
     */
    fun attemptMatch(memberId: Long): MatchingResponse.QueueStatus? {
        val myScore = queueRepository.score(memberId) ?: return null
        val joinedAt = queueRepository.findJoinedAt(memberId) ?: Instant.now()
        val tolerance = MatchingScoreCalculator.tolerance(joinedAt)

        val candidateIds = queueRepository.candidatesInRange(myScore - tolerance, myScore + tolerance)
            .filter { it != memberId }
        if (candidateIds.isEmpty()) return null

        val myProfile = profileRepository.findProfile(memberId) ?: return null
        val myFilter = queueRepository.findFilter(memberId)

        val ranked = candidateIds
            .filterNot { candidateId -> isBlockedEitherWay(memberId, candidateId) }
            .mapNotNull { candidateId -> profileRepository.findProfile(candidateId)?.let { candidateId to it } }
            .filter { (candidateId, candidateProfile) ->
                val candidateFilter = queueRepository.findFilter(candidateId)
                matchesFilter(myFilter, candidateProfile) && matchesFilter(candidateFilter, myProfile)
            }
            .map { (candidateId, candidateProfile) ->
                val commonInterests = candidateProfile.interests.intersect(myProfile.interests).size
                val candidateJoinedAt = queueRepository.findJoinedAt(candidateId) ?: Instant.now()
                Triple(candidateId, commonInterests, candidateJoinedAt)
            }
            // 공통 관심사 많은 순 우선, 동점이면 대기시간이 긴(joinedAt이 이른) 사람 우선
            .sortedWith(compareByDescending<Triple<Long, Int, Instant>> { it.second }.thenBy { it.third })

        for ((partnerId, _, partnerJoinedAt) in ranked) {
            val partnerScore = queueRepository.score(partnerId) ?: continue
            // ZSET.remove의 원자적 반환값을 CAS 가드로 사용: 다른 스레드가 먼저 채갔다면 false
            if (!queueRepository.remove(partnerId)) continue

            if (!queueRepository.remove(memberId)) {
                // 그 사이 내가 다른 스레드에 의해 매칭되어 사라졌다면, 잡았던 partner를 원상복구한다
                queueRepository.add(partnerId, partnerScore)
                queueRepository.saveJoinedAt(partnerId, partnerJoinedAt)
                return null
            }
            queueRepository.removeJoinedAt(partnerId)
            queueRepository.removeFilter(partnerId)
            queueRepository.removeJoinedAt(memberId)
            queueRepository.removeFilter(memberId)

            return connect(memberId, partnerId)
        }
        return null
    }

    private fun matchesFilter(filter: MatchingQueueFilter?, targetProfile: Profile): Boolean {
        if (filter == null || !filter.isPresent()) return true

        if (filter.languageLevel != null && targetProfile.languageLevel != filter.languageLevel) {
            return false
        }

        if (filter.minAge != null || filter.maxAge != null) {
            val age = ageOf(targetProfile.birthDay) ?: return false
            if (filter.minAge != null && age < filter.minAge) return false
            if (filter.maxAge != null && age > filter.maxAge) return false
        }

        return true
    }

    private fun ageOf(birthDay: LocalDate?): Int? = birthDay?.let { Period.between(it, LocalDate.now()).years }

    private fun isBlockedEitherWay(memberId: Long, otherId: Long): Boolean =
        relationshipRepository.findBlock(memberId, otherId) != null ||
            relationshipRepository.findBlock(otherId, memberId) != null

    private fun connect(memberId: Long, partnerId: Long): MatchingResponse.QueueStatus {
        val me = memberRepository.findById(memberId) ?: throw LanglezException(404, "member.not-found")
        val partner = memberRepository.findById(partnerId) ?: throw LanglezException(404, "member.not-found")

        val room = chatService.getOrCreateRoom(memberId, partner.username)

        matchBroadcaster.broadcastMatched(memberId, room.id!!, partner.username)
        matchBroadcaster.broadcastMatched(partnerId, room.id!!, me.username)

        return MatchingResponse.QueueStatus(MatchingResponse.QueueStatus.Status.MATCHED, room.id)
    }
}


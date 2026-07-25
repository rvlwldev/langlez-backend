package com.langlez.matching.application

import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.matching.api.MatchingRequest
import com.langlez.matching.api.MatchingResponse
import com.langlez.matching.domain.RecommendationRepository
import com.langlez.member.domain.Member
import com.langlez.member.domain.MemberRepository
import com.langlez.profile.domain.Profile
import com.langlez.profile.domain.ProfileRepository
import com.langlez.relationship.domain.RelationshipRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Duration
import java.time.LocalDate
import java.time.Period

/**
 * PLAN.md Phase 5 일별/시간별 추천 목록.
 * 무료 회원: 매일 자정 15명 갱신 / 유료 회원: 매시간 15명 갱신.
 * 규칙: 같은 languageLevel 우선 + 랜덤 셔플, 차단/이미 매칭(채팅방 존재)한 상대는 제외.
 * 고급 필터(성별/나이대/언어레벨)는 유료 전용 — MEMBER role이 지정하면 403.
 */
@Service
class RecommendationService(
    private val profileRepository: ProfileRepository,
    private val memberRepository: MemberRepository,
    private val relationshipRepository: RelationshipRepository,
    private val chatRoomRepository: ChatRoomRepository,
    private val recommendationRepository: RecommendationRepository,
) {

    fun refreshFreeMembers() = refreshAll(Duration.ofDays(1)) { it.member.role == Member.Role.MEMBER }

    fun refreshPremiumMembers() = refreshAll(Duration.ofHours(1)) { it.member.role != Member.Role.MEMBER }

    private fun refreshAll(ttl: Duration, predicate: (Profile) -> Boolean) {
        val all = profileRepository.findAllProfiles()
        all.filter(predicate).forEach { myProfile -> refreshFor(myProfile, all, ttl) }
    }

    fun refreshFor(myProfile: Profile, all: List<Profile>, ttl: Duration) {
        val myId = myProfile.id
        val blockedIds = relationshipRepository.findBlocks(myId, null, EXCLUSION_SCAN_SIZE).map { it.blockedId }.toSet()
        val matchedIds = chatRoomRepository.findByParticipant(myId, null, EXCLUSION_SCAN_SIZE)
            .flatMap { it.participantIds }
            .filter { it != myId }
            .toSet()
        val excluded = blockedIds + matchedIds + myId

        val candidates = all.filter { it.id !in excluded }
        val (sameLevel, otherLevel) = candidates.partition {
            myProfile.languageLevel != null && it.languageLevel == myProfile.languageLevel
        }
        val picked = (sameLevel.shuffled() + otherLevel.shuffled()).take(RECOMMENDATION_SIZE)

        val usernames = memberRepository.findByIds(picked.map { it.id }).map { it.username }
        recommendationRepository.save(myId, usernames, ttl)
    }

    @Transactional(readOnly = true)
    fun getRecommendations(
        memberId: Long,
        role: String,
        filter: MatchingRequest.RecommendationFilter,
    ): MatchingResponse.RecommendationList {
        if (filter.isPresent() && role == Member.Role.MEMBER.name) {
            throw LanglezException(403, "matching.recommendation.premium-only-filter")
        }

        val usernames = recommendationRepository.find(memberId) ?: emptyList()
        val summaries = usernames.mapNotNull { username ->
            val member = memberRepository.findByUsername(username) ?: return@mapNotNull null
            if (filter.isPresent()) {
                val profile = profileRepository.findProfile(member.id) ?: return@mapNotNull null
                if (!matchesFilter(profile, filter)) return@mapNotNull null
            }
            MatchingResponse.MemberSummary(member.username, member.nickname)
        }
        return MatchingResponse.RecommendationList(summaries)
    }

    private fun matchesFilter(profile: Profile, filter: MatchingRequest.RecommendationFilter): Boolean {
        if (filter.gender != null && profile.gender != filter.gender) return false
        if (filter.languageLevel != null && profile.languageLevel != filter.languageLevel) return false
        if (filter.minAge != null || filter.maxAge != null) {
            val age = ageOf(profile.birthDay) ?: return false
            if (filter.minAge != null && age < filter.minAge) return false
            if (filter.maxAge != null && age > filter.maxAge) return false
        }
        return true
    }

    private fun ageOf(birthDay: LocalDate?): Int? = birthDay?.let { Period.between(it, LocalDate.now()).years }

    companion object {
        private const val RECOMMENDATION_SIZE = 15
        private const val EXCLUSION_SCAN_SIZE = 1000
    }
}


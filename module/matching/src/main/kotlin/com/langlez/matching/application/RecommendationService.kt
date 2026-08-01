package com.langlez.matching.application

import com.langlez.chat.domain.ChatRoomRepository
import com.langlez.core.LanglezException
import com.langlez.matching.api.MatchingRequest
import com.langlez.matching.api.MatchingResponse
import com.langlez.matching.domain.RecommendationRepository
import com.langlez.member.application.MemberRepository
import com.langlez.member.domain.MemberRole
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

    fun refreshFreeMembers() = refreshAll(Duration.ofDays(1)) { it.member.role == MemberRole.MEMBER }

    fun refreshPremiumMembers() = refreshAll(Duration.ofHours(1)) { it.member.role != MemberRole.MEMBER }

    private fun refreshAll(ttl: Duration, predicate: (Profile) -> Boolean) {
        val all = profileRepository.findAllProfiles()
        // 유저마다 전체 인구를 필터링(O(N²))하는 대신, 레벨별 풀을 한 번만 만들어두고
        // 유저별로는 그 풀에서 무작위 샘플링만 한다.
        val byLevel = all.groupBy { it.languageLevel }
        all.filter(predicate).forEach { myProfile -> refreshFor(myProfile, all, byLevel, ttl) }
    }

    fun refreshFor(
        myProfile: Profile,
        all: List<Profile>,
        byLevel: Map<Profile.LanguageLevel?, List<Profile>>,
        ttl: Duration,
    ) {
        val myId = myProfile.id
        val blockedIds = relationshipRepository.findBlocks(myId, null, EXCLUSION_SCAN_SIZE).map { it.blockedId }.toSet()
        val matchedIds = chatRoomRepository.findByParticipant(myId, null, EXCLUSION_SCAN_SIZE)
            .flatMap { it.participantIds }
            .filter { it != myId }
            .toSet()
        val excluded = blockedIds + matchedIds + myId

        val sameLevelPool = myProfile.languageLevel?.let { byLevel[it] } ?: emptyList()
        val picked = LinkedHashSet<Profile>()
        picked += sampleExcluding(sameLevelPool, excluded, RECOMMENDATION_SIZE)
        if (picked.size < RECOMMENDATION_SIZE) {
            val pickedIds = picked.mapTo(mutableSetOf()) { it.id }
            picked += sampleExcluding(all, excluded + pickedIds, RECOMMENDATION_SIZE - picked.size)
        }

        val usernames = memberRepository.findByIds(picked.map { it.id }).map { it.username }
        recommendationRepository.save(myId, usernames, ttl)
    }

    /**
     * pool에서 excluded를 뺀 후보 중 count명을 무작위로 뽑는다. 전체를 필터링하지 않고
     * 무작위 인덱스를 뽑아보는 방식이라, excluded 비율이 지나치게 높은 극단적 경우
     * count에 못 미칠 수 있다(허용 가능한 트레이드오프).
     */
    private fun sampleExcluding(pool: List<Profile>, excluded: Set<Long>, count: Int): List<Profile> {
        if (pool.isEmpty() || count <= 0) return emptyList()
        val result = LinkedHashSet<Profile>()
        val tried = HashSet<Int>()
        val maxAttempts = (count * SAMPLE_ATTEMPT_MULTIPLIER).coerceAtMost(pool.size)
        while (result.size < count && tried.size < maxAttempts && tried.size < pool.size) {
            val idx = pool.indices.random()
            if (tried.add(idx)) {
                val candidate = pool[idx]
                if (candidate.id !in excluded) result.add(candidate)
            }
        }
        return result.toList()
    }

    @Transactional(readOnly = true)
    fun getRecommendations(
        memberId: Long,
        role: String,
        filter: MatchingRequest.RecommendationFilter,
    ): MatchingResponse.RecommendationList {
        if (filter.isPresent() && role == MemberRole.MEMBER.name) {
            throw LanglezException(403, "matching.recommendation.premium-only-filter")
        }

        val usernames = recommendationRepository.find(memberId) ?: emptyList()
        if (usernames.isEmpty()) return MatchingResponse.RecommendationList(emptyList())

        // 추천 인원수만큼 단건 조회하면 N+1이 되므로 회원/프로필 모두 한 번에 읽는다.
        val membersByUsername = memberRepository.findByUsernames(usernames).associateBy { it.username }
        val profilesById = if (filter.isPresent()) {
            profileRepository.findProfiles(membersByUsername.values.map { it.id }).associateBy { it.id }
        } else {
            emptyMap()
        }

        // 추천 순서를 보존하기 위해 저장된 username 순서로 순회한다.
        val summaries = usernames.mapNotNull { username ->
            val member = membersByUsername[username] ?: return@mapNotNull null
            if (filter.isPresent()) {
                val profile = profilesById[member.id] ?: return@mapNotNull null
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
        private const val SAMPLE_ATTEMPT_MULTIPLIER = 5
    }
}


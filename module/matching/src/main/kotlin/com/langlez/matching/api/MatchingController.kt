package com.langlez.matching.api

import com.langlez.matching.application.MatchingService
import com.langlez.matching.application.RecommendationService
import com.langlez.profile.domain.Profile
import com.langlez.security.web.MemberID
import com.langlez.security.web.MemberRole
import org.springframework.http.HttpStatus.NO_CONTENT
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/matching")
class MatchingController(
    private val matchingService: MatchingService,
    private val recommendationService: RecommendationService,
) {

    @PostMapping("/queue")
    fun joinQueue(
        @MemberID memberId: Long,
        @RequestBody(required = false) filter: MatchingRequest.QueueFilter? = null,
    ): MatchingResponse.QueueStatus =
        matchingService.joinQueue(memberId, filter?.toDomain())


    @DeleteMapping("/queue")
    @ResponseStatus(NO_CONTENT)
    fun leaveQueue(@MemberID memberId: Long) {
        matchingService.leaveQueue(memberId)
    }

    @GetMapping("/recommendations")
    fun getRecommendations(
        @MemberID memberId: Long,
        @MemberRole role: String,
        @RequestParam(required = false) gender: Profile.Gender?,
        @RequestParam(required = false) minAge: Int?,
        @RequestParam(required = false) maxAge: Int?,
        @RequestParam(required = false) languageLevel: Profile.LanguageLevel?,
    ): MatchingResponse.RecommendationList =
        recommendationService.getRecommendations(
            memberId,
            role,
            MatchingRequest.RecommendationFilter(gender, minAge, maxAge, languageLevel),
        )
}

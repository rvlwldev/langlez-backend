package com.langlez.matching.api

class MatchingResponse {

    data class QueueStatus(
        val status: Status,
        val roomId: String? = null,
    ) {
        enum class Status { WAITING, MATCHED }
    }

    data class MemberSummary(
        val username: String,
        val nickname: String,
    )

    data class RecommendationList(
        val members: List<MemberSummary>,
    )
}

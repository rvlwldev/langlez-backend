package com.langlez.relationship.api

import com.langlez.relationship.application.RelationshipResult

class RelationshipResponse {
    data class MemberSummary(val username: String, val nickname: String) {
        companion object {
            fun from(summary: RelationshipResult.MemberSummary) =
                MemberSummary(summary.username, summary.nickname)
        }
    }

    data class CursorList(val nextCursor: Long?, val members: List<MemberSummary>) {
        companion object {
            fun from(result: RelationshipResult.CursorList) =
                CursorList(
                    nextCursor = result.nextCursor,
                    members = result.members.map { MemberSummary.from(it) }
                )
        }
    }
}

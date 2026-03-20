package com.langlez.relationship.api

class RelationshipResponse {
    data class MemberSummary(val username: String, val nickname: String)
    data class CursorList(val nextCursor: Long?, val members: List<MemberSummary>)
}

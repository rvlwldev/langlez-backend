package com.langlez.relationship.application

sealed interface RelationshipResult {
    data class MemberSummary(val username: String, val nickname: String) : RelationshipResult
    data class CursorList(val nextCursor: Long?, val members: List<MemberSummary>) : RelationshipResult
}

package com.langlez.relationship.domain

/**
 * 팔로우·차단·신고 저장소 포트.
 *
 * 목록은 전부 커서 페이징이다. 정렬·커서 기준은 `created_at` 이 아니라 행 id 다 —
 * 인스턴스마다 시계가 어긋나면 같은 밀리초에 들어온 행이 페이지 경계에서 겹치거나 사라진다.
 */
interface RelationshipRepository {

    /** 목록 한 줄. `id` 는 다음 페이지 커서로 쓸 행 id, `memberId` 는 상대 회원 id. */
    data class Edge(val id: Long, val memberId: Long)

    fun save(follow: Follow): Follow
    fun findFollow(followerId: Long, followedId: Long): Follow?
    fun deleteFollow(followerId: Long, followedId: Long)

    /** 나를 팔로우한 사람들 */
    fun findFollowers(memberId: Long, size: Int, cursor: Long?): List<Edge>

    /** 내가 팔로우한 사람들 */
    fun findFollowings(memberId: Long, size: Int, cursor: Long?): List<Edge>

    fun save(block: Block): Block
    fun findBlock(blockerId: Long, blockedId: Long): Block?
    fun deleteBlock(blockerId: Long, blockedId: Long)

    /** 내가 차단한 사람들 */
    fun findBlocks(memberId: Long, size: Int, cursor: Long?): List<Edge>

    fun save(report: Report): Report

    /**
     * 같은 신고가 이미 있는지.
     *
     * 카프카는 at-least-once 라 같은 신고 이벤트가 두 번 배달된다. 이벤트에 고유 id 가 없어
     * (신고자, 출처 종류, 출처 id, 트리거 메시지) 조합을 신고 하나의 식별자로 본다.
     */
    fun existsReport(
        reporterId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        triggerMessageId: String?,
    ): Boolean
}

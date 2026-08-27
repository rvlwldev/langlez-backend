package com.langlez.relationship.application

import com.langlez.core.BlockQuery
import com.langlez.core.event.relationship.MemberFollowedEvent
import com.langlez.exception.LanglezException
import com.langlez.member.domain.MemberRepository
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.RelationshipRepository.Edge
import com.langlez.relationship.domain.Report
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 팔로우·차단·신고 유스케이스.
 *
 * 차단 여부 판정은 이 모듈이 구현한 `core.BlockQuery` 를 그대로 쓴다 —
 * 양방향 판정 규칙이 chat 과 갈라지면 한쪽에서만 막히는 구멍이 생긴다.
 */
@Service
class RelationshipService(
    private val repo: RelationshipRepository,
    private val members: MemberRepository,
    private val blocks: BlockQuery,
    private val publisher: ApplicationEventPublisher,
) {

    /**
     * 팔로우.
     *
     * 이미 팔로우 중이면 조용히 끝낸다(멱등). 더블 탭이나 재시도로 409 를 돌려줄 이유가 없다.
     * 남은 경합은 UNQ_MEMBER_FOLLOW 가 막는다.
     */
    @Transactional
    fun follow(memberId: Long, targetId: Long) {
        requireMemberExists(targetId)
        if (blocks.isBlockedBetween(memberId, targetId)) throw LanglezException(FORBIDDEN, "social.follow.blocked")
        if (repo.findFollow(memberId, targetId) != null) return

        // 저장 결과의 행 id 를 이벤트에 싣는다. 컨슈머 중복 판정이 이 값으로 갈린다
        // (언팔로우 후 재팔로우와 카프카 재배달을 구분하는 유일한 값이다).
        val follow = repo.save(newFollow(memberId, targetId))
        publisher.publishEvent(MemberFollowedEvent(follow.id, memberId, targetId))
    }

    /** 언팔로우는 없는 관계를 지워도 성공이다. 클라이언트가 상태를 몰라도 되게 한다. */
    @Transactional
    fun unfollow(memberId: Long, targetId: Long) = repo.deleteFollow(memberId, targetId)

    @Transactional(readOnly = true)
    fun listFollowers(memberId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> =
        toViews(repo.findFollowers(memberId, size, cursor))

    @Transactional(readOnly = true)
    fun listFollowings(memberId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> =
        toViews(repo.findFollowings(memberId, size, cursor))

    /** 남의 프로필에서 보는 팔로워 목록. */
    @Transactional(readOnly = true)
    fun listFollowersOf(viewerId: Long, targetId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> {
        requireVisible(viewerId, targetId)

        return toViews(repo.findFollowers(targetId, size, cursor))
    }

    /** 남의 프로필에서 보는 팔로잉 목록. */
    @Transactional(readOnly = true)
    fun listFollowingsOf(viewerId: Long, targetId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> {
        requireVisible(viewerId, targetId)

        return toViews(repo.findFollowings(targetId, size, cursor))
    }

    /**
     * 차단.
     *
     * 팔로우 관계를 양방향으로 끊는다 — 차단해 놓고 서로 팔로우 목록에 남아 있으면
     * 타임라인·알림이 계속 흘러 차단이 무의미해진다.
     * 이미 차단된 상대여도 해제는 다시 보장한다(과거에 반쪽만 끊긴 데이터를 수습한다).
     */
    @Transactional
    fun block(memberId: Long, targetId: Long) {
        requireMemberExists(targetId)

        if (repo.findBlock(memberId, targetId) == null) repo.save(newBlock(memberId, targetId))

        repo.deleteFollow(memberId, targetId)
        repo.deleteFollow(targetId, memberId)
    }

    @Transactional
    fun unblock(memberId: Long, targetId: Long) = repo.deleteBlock(memberId, targetId)

    @Transactional(readOnly = true)
    fun listBlocks(memberId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> =
        toViews(repo.findBlocks(memberId, size, cursor))

    /**
     * 신고 접수.
     *
     * 같은 신고가 이미 있으면 아무것도 하지 않는다. 카프카 재배달·클라이언트 재시도로
     * 같은 신고가 여러 행 쌓이면 운영자가 같은 건을 몇 번씩 처리하게 된다.
     */
    @Transactional
    fun report(
        reporterId: Long,
        reportedUserId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        reason: String,
        triggerMessageId: String? = null,
    ) {
        if (repo.existsReport(reporterId, sourceType, sourceId, triggerMessageId)) return

        repo.save(Report(reporterId, reportedUserId, sourceType, sourceId, reason, triggerMessageId))
    }

    /**
     * 목록에 회원 정보를 붙인다.
     *
     * 조회는 한 번뿐이고, 그 사이 탈퇴해 사라진 회원은 빠진다 —
     * 팔로우 행은 회원 삭제와 별개라 껍데기 id 만 남을 수 있다.
     */
    private fun toViews(edges: List<Edge>): List<RelationshipMemberView> {
        if (edges.isEmpty()) return emptyList()

        val members = members.findAll(edges.map { it.memberId }).associateBy { it.id }

        return edges.mapNotNull { edge ->
            members[edge.memberId]?.let { RelationshipMemberView(edge.id, it.id, it.handle, it.imageUrl) }
        }
    }

    /**
     * 차단 관계면 목록 자체를 막는다. 걸러 봐야 전부 빠진다 —
     * `EchoService.memberTimeline` 이 같은 판단을 한다.
     */
    private fun requireVisible(viewerId: Long, targetId: Long) {
        if (blocks.isBlockedBetween(viewerId, targetId)) throw LanglezException(FORBIDDEN, "social.blocked")
    }

    private fun requireMemberExists(id: Long) {
        members.find(id) ?: throw LanglezException(NOT_FOUND, "member.not-found")
    }

    /** 자기 자신 여부는 엔티티가 막는다. 여기선 상태코드만 붙인다. */
    private fun newFollow(followerId: Long, followedId: Long) =
        try {
            Follow(followerId, followedId)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }

    private fun newBlock(blockerId: Long, blockedId: Long) =
        try {
            Block(blockerId, blockedId)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }
}

/** 팔로워/팔로잉/차단 목록 한 줄. `cursor` 는 다음 페이지 요청에 그대로 넣는 값이다. */
data class RelationshipMemberView(
    val cursor: Long,
    val memberId: Long,
    val handle: String,
    val imageUrl: String?,
)

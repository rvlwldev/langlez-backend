package com.langlez.relationship.application

import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberReader
import com.langlez.relationship.contract.BlockReader
import com.langlez.relationship.contract.MemberFollowedEvent
import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.RelationshipRepository.Edge
import com.langlez.relationship.domain.Report
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.FORBIDDEN
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate

/**
 * 팔로우·차단·신고 유스케이스.
 *
 * 차단 여부 판정은 이 모듈이 구현한 `BlockReader` 를 그대로 쓴다 —
 * 양방향 판정 규칙이 chat 과 갈라지면 한쪽에서만 막히는 구멍이 생긴다.
 *
 * 회원 정보는 `member-api` 의 `MemberReader` 로만 본다. member 의 domain 계층(`MemberRepository`)을
 * 직접 참조하면 모듈 경계가 무너지고, 그 포트가 원격이 될 때 여기가 통째로 깨진다.
 * `MemberReader` 호출은 전부 트랜잭션 밖에서 끝낸다.
 */
@Service
class RelationshipService(
    private val repo: RelationshipRepository,
    private val members: MemberReader,
    private val blocks: BlockReader,
    private val publisher: ApplicationEventPublisher,
    private val tx: TransactionTemplate,
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    /**
     * 팔로우.
     *
     * 이미 팔로우 중이면 조용히 끝낸다(멱등). 더블 탭이나 재시도로 409 를 돌려줄 이유가 없다.
     * 남은 경합은 UNQ_MEMBER_FOLLOW 가 막는다.
     *
     * 존재·차단 판정은 다른 모듈의 포트라 트랜잭션 밖에서 먼저 끝낸다. 판정과 저장 사이에
     * 상대가 탈퇴하거나 차단을 걸면 팔로우 행 하나가 남는데, 이후 조회가 전부 다시 차단을 보고
     * 사라진 회원은 `toViews` 에서 빠지므로 노출로 이어지지 않는다.
     */
    fun follow(memberId: Long, targetId: Long) {
        requireMemberExists(targetId)
        if (blocks.isBlockedBetween(memberId, targetId)) throw LanglezException(FORBIDDEN, "social.follow.blocked")

        tx.execute {
            if (repo.findFollow(memberId, targetId) != null) return@execute

            // 저장 결과의 행 id 를 이벤트에 싣는다. 컨슈머 중복 판정이 이 값으로 갈린다
            // (언팔로우 후 재팔로우와 카프카 재배달을 구분하는 유일한 값이다).
            val follow = repo.save(newFollow(memberId, targetId))
            publisher.publishEvent(MemberFollowedEvent(follow.id, memberId, targetId))
        }
    }

    /** 언팔로우는 없는 관계를 지워도 성공이다. 클라이언트가 상태를 몰라도 되게 한다. */
    @Transactional
    fun unfollow(memberId: Long, targetId: Long) = repo.deleteFollow(memberId, targetId)

    /**
     * 목록 조회에는 트랜잭션을 걸지 않는다. 저장소 읽기 한 번 + `MemberReader` 배치 조회 한 번인데,
     * 그 포트가 원격이 되면 트랜잭션이 커넥션을 쥔 채 네트워크를 기다린다. 감싸도 한 스냅샷이
     * 되지도 않는다 — 회원 정보는 이미 다른 저장소다.
     */
    fun listFollowers(memberId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> =
        toViews(repo.findFollowers(memberId, size, cursor))

    fun listFollowings(memberId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> =
        toViews(repo.findFollowings(memberId, size, cursor))

    /** 남의 프로필에서 보는 팔로워 목록. */
    fun listFollowersOf(viewerId: Long, targetId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> {
        requireVisible(viewerId, targetId)

        return toViews(repo.findFollowers(targetId, size, cursor))
    }

    /** 남의 프로필에서 보는 팔로잉 목록. */
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
     *
     * `follow` 와 같은 이유로 존재 확인은 트랜잭션 밖이다. 확인과 저장 사이에 상대가 탈퇴해도
     * 차단 행이 남을 뿐이고, 그건 원래 탈퇴 회원에게도 남겨두는 데이터다.
     */
    fun block(memberId: Long, targetId: Long) {
        requireMemberExists(targetId)

        tx.execute {
            if (repo.findBlock(memberId, targetId) == null) repo.save(newBlock(memberId, targetId))

            repo.deleteFollow(memberId, targetId)
            repo.deleteFollow(targetId, memberId)
        }
    }

    @Transactional
    fun unblock(memberId: Long, targetId: Long) = repo.deleteBlock(memberId, targetId)

    fun listBlocks(memberId: Long, size: Int, cursor: Long?): List<RelationshipMemberView> =
        toViews(repo.findBlocks(memberId, size, cursor))

    /**
     * 신고 접수.
     *
     * 같은 신고가 이미 있으면 아무것도 하지 않는다(멱등). 카프카 재배달·클라이언트 재시도로
     * 같은 신고가 여러 행 쌓이면 운영자가 같은 건을 몇 번씩 처리하게 된다.
     *
     * **트랜잭션을 걸지 않는다.** 읽기 하나 + 쓰기 하나뿐이라 묶어도 얻는 게 없고,
     * 묶으면 오히려 아래 유니크 충돌을 삼킬 수 없다 — 하이버네이트가 제약 위반 시점에
     * 트랜잭션을 rollback-only 로 표시해서, 예외를 잡고 정상 반환해도 커밋에서
     * `UnexpectedRollbackException` 이 난다. `repo.save` 는 자기 트랜잭션을 갖는다.
     */
    fun report(
        reporterId: Long,
        reportedUserId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        reason: String,
        triggerMessageId: String? = null,
    ) {
        if (repo.existsReport(reporterId, sourceType, sourceId, triggerMessageId)) return

        try {
            repo.save(Report(reporterId, reportedUserId, sourceType, sourceId, reason, triggerMessageId))
        } catch (e: DataIntegrityViolationException) {
            // UNQ_REPORT_IDENTITY 충돌 = 위 검사와 저장 사이에 같은 신고가 들어왔다. 정상 상황이라 삼킨다.
            //
            // 두 호출 경로가 같은 결론이라 여기서 한 번만 흡수한다.
            // 컨슈머(chat-user-reported): 이미 저장돼 있으니 성공으로 보고 오프셋을 넘겨야 한다.
            //   올리면 재시도를 다 쓰고 DLT 로 간다 — 저장은 됐는데 사람이 DLT 를 뒤지게 된다.
            // HTTP(POST /reports): 두 번 눌러도 204 다. 접수 전에도 접수 후에도 응답이 같아야
            //   재시도가 안전하고, 이미 존재 검사가 걸렀을 때와 동작이 갈리지 않는다.
            //
            // 재시도하지 않는다(@Retryable 금지). 몇 번을 다시 넣어도 같은 행이 이미 있다.
            logger.debug("중복 신고를 무시한다. reporter={} source={}:{}", reporterId, sourceType, sourceId, e)
        }
    }

    /**
     * 목록에 회원 정보를 붙인다.
     *
     * 조회는 한 번뿐이고, 그 사이 탈퇴해 사라진 회원은 빠진다 —
     * 팔로우 행은 회원 삭제와 별개라 껍데기 id 만 남을 수 있다.
     */
    private fun toViews(edges: List<Edge>): List<RelationshipMemberView> {
        if (edges.isEmpty()) return emptyList()

        val infos = members.findProfileInfos(edges.map { it.memberId })

        return edges.mapNotNull { edge ->
            infos[edge.memberId]?.let { RelationshipMemberView(edge.id, it.id, it.handle, it.imageUrl) }
        }
    }

    /**
     * 차단 관계면 목록 자체를 막는다. 걸러 봐야 전부 빠진다 —
     * `EchoService.memberTimeline` 이 같은 판단을 한다.
     *
     * 포트 호출이라 호출부가 트랜잭션을 열기 전에 부른다.
     */
    private fun requireVisible(viewerId: Long, targetId: Long) {
        if (blocks.isBlockedBetween(viewerId, targetId)) throw LanglezException(FORBIDDEN, "social.blocked")
    }

    /**
     * 행이 있으면 통과다. 탈퇴 회원도 행은 남으므로(지우지 않는 정책이다) 여기서 404 가 되지 않는데,
     * `MemberRepository.find` 를 쓰던 이전 동작 그대로다 — 포트 교체로 판정을 바꾸지 않았다.
     *
     * `findStatus` 도 같은 조회를 타서 결과는 같지만 쓰지 않는다. 상태값이 필요 없는 자리에
     * 상태 포트를 쓰면 다음 사람이 "정지 회원은 여기서 걸러진다"고 오해한다.
     */
    private fun requireMemberExists(id: Long) {
        members.findProfileInfo(id) ?: throw LanglezException(NOT_FOUND, "member.not-found")
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

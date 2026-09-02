package com.langlez.block.application

import com.langlez.block.contract.MemberBlockedEvent
import com.langlez.block.domain.Block
import com.langlez.block.domain.BlockRepository
import com.langlez.block.domain.BlockRepository.Edge
import com.langlez.exception.LanglezException
import com.langlez.member.contract.MemberReader
import org.springframework.context.ApplicationEventPublisher
import org.springframework.http.HttpStatus.BAD_REQUEST
import org.springframework.http.HttpStatus.NOT_FOUND
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant

/**
 * 차단 유스케이스.
 *
 * 회원 정보는 `member-api` 의 `MemberReader` 로만 본다. **포트 호출은 전부 트랜잭션 밖에서 끝낸다** —
 * 이 포트는 곧 gRPC/HTTP 로 대체되고, 트랜잭션 안에 두면 커넥션을 쥔 채 네트워크를 기다린다.
 */
@Service
class BlockService(
    private val repo: BlockRepository,
    private val members: MemberReader,
    private val publisher: ApplicationEventPublisher,
    private val tx: TransactionTemplate,
) {

    /**
     * 차단.
     *
     * 차단하면 서로의 팔로우 관계를 양방향으로 끊어야 한다 — 차단해 놓고 서로 팔로우 목록에
     * 남아 있으면 타임라인·알림이 계속 흘러 차단이 무의미해진다. 그런데 **팔로우 행은 follow
     * 모듈 소유라 여기서 직접 지울 수 없다.** `MemberBlockedEvent` 를 발행하고
     * `FollowConsumer` 가 `member-blocked` 를 받아 지운다.
     *
     * ## 이벤트가 소비되기 전까지 팔로우 행이 남는 창은 사용자에게 보이지 않는다
     *
     * **차단의 효력은 즉시고, 팔로우 행 정리만 지연된다.** 차단 행이 커밋되는 순간부터
     * `BlockReader.isBlockedBetween` 이 true 라, 팔로우 행을 읽는 경로가 전부 그 앞에서 막힌다 —
     * `FollowService`(목록·신규 팔로우), `EchoService`(타임라인), `ChatService`(메시지)가
     * 모두 차단을 먼저 본다. 남아 있는 팔로우 행을 통과해 무언가가 보이는 경로는 없다.
     *
     * **이 문단을 지우지 마라.** 없으면 다음 사람이 "차단했는데 팔로우가 남아 있다"를 버그로 보고
     * 동기 호출로 되돌린다. 그렇게 되돌리면 모듈 경계가 다시 무너진다.
     *
     * ## 이미 차단된 상대를 다시 차단해도 이벤트를 발행한다
     *
     * 과거에 반쪽만 끊긴 데이터를 수습하는 경로다. 그래서 차단 행 저장 여부와 무관하게
     * 항상 발행한다. `MemberBlockedEvent.occurredAt` 이 요청마다 달라지는 이유도 이것이다 —
     * 같은 페이로드면 `MessageDeduplicator` 가 수습 이벤트를 재배달로 보고 걷어낸다.
     *
     * `follow` 와 같은 이유로 존재 확인은 트랜잭션 밖이다. 확인과 저장 사이에 상대가 탈퇴해도
     * 차단 행이 남을 뿐이고, 그건 원래 탈퇴 회원에게도 남겨두는 데이터다.
     */
    fun block(memberId: Long, targetId: Long) {
        requireMemberExists(targetId)

        tx.execute {
            if (repo.find(memberId, targetId) == null) repo.save(newBlock(memberId, targetId))

            // 트랜잭션 안에서 발행해야 BEFORE_COMMIT 리스너가 아웃박스 행을 같은 트랜잭션에 넣는다.
            publisher.publishEvent(
                MemberBlockedEvent(
                    blockerId = memberId,
                    blockedId = targetId,
                    occurredAt = Instant.now().toEpochMilli(),
                )
            )
        }
    }

    /** 차단 해제는 없는 관계를 지워도 성공이다. 팔로우는 복구하지 않는다 — 끊은 쪽의 의사를 되돌릴 근거가 없다. */
    @Transactional
    fun unblock(memberId: Long, targetId: Long) = repo.delete(memberId, targetId)

    /**
     * 목록 조회에는 트랜잭션을 걸지 않는다. 저장소 읽기 한 번 + `MemberReader` 배치 조회 한 번인데,
     * 그 포트가 원격이 되면 트랜잭션이 커넥션을 쥔 채 네트워크를 기다린다. 감싸도 한 스냅샷이
     * 되지도 않는다 — 회원 정보는 이미 다른 저장소다.
     */
    fun listBlocks(memberId: Long, size: Int, cursor: Long?): List<BlockMemberView> =
        toViews(repo.findBlocks(memberId, size, cursor))

    /**
     * 목록에 회원 정보를 붙인다.
     *
     * 조회는 한 번뿐이고, 그 사이 탈퇴해 사라진 회원은 빠진다 —
     * 차단 행은 회원 삭제와 별개라 껍데기 id 만 남을 수 있다.
     */
    private fun toViews(edges: List<Edge>): List<BlockMemberView> {
        if (edges.isEmpty()) return emptyList()

        val infos = members.findProfileInfos(edges.map { it.memberId })

        return edges.mapNotNull { edge ->
            infos[edge.memberId]?.let { BlockMemberView(edge.id, it.id, it.handle, it.imageUrl) }
        }
    }

    /**
     * 행이 있으면 통과다. 탈퇴 회원도 행은 남으므로(지우지 않는 정책이다) 여기서 404 가 되지 않는데,
     * `MemberRepository.find` 를 쓰던 이전 동작 그대로다 — 포트 교체로 판정을 바꾸지 않았다.
     */
    private fun requireMemberExists(id: Long) {
        members.findProfileInfo(id) ?: throw LanglezException(NOT_FOUND, "member.not-found")
    }

    /** 자기 자신 여부는 엔티티가 막는다. 여기선 상태코드만 붙인다. */
    private fun newBlock(blockerId: Long, blockedId: Long) =
        try {
            Block(blockerId, blockedId)
        } catch (e: IllegalArgumentException) {
            throw LanglezException(BAD_REQUEST, e.message, e)
        }
}

/** 차단 목록 한 줄. `cursor` 는 다음 페이지 요청에 그대로 넣는 값이다. */
data class BlockMemberView(
    val cursor: Long,
    val memberId: Long,
    val handle: String,
    val imageUrl: String?,
)

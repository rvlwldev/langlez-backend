package com.langlez.relationship.infrastructure

import com.langlez.relationship.contract.BlockQuery
import com.langlez.relationship.contract.FollowQuery
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import com.langlez.relationship.domain.QBlock.Companion.block as QBlock

/**
 * relationship 이 다른 모듈에 내주는 조회 포트 구현.
 *
 * 차단·팔로우 둘 다 relationship 저장소를 읽어 매핑하는 것뿐이라 어댑터를 나누지 않는다.
 *
 * echo 홈 타임라인이 팔로우 조회를 쓴다. 구현이 없으면 타임라인이 통째로 503 이 되므로
 * relationship 모듈이 반드시 이 빈을 올려야 한다.
 */
@Repository
class RelationshipQueryImpl(
    private val blocks: BlockJpaRepository,
    private val follows: FollowJpaRepository,
    private val repo: RelationshipRepository,
    private val dsl: JPAQueryFactory,
) : BlockQuery, FollowQuery {

    /**
     * 차단 행은 단방향(blocker → blocked)으로만 저장된다.
     * 채팅·매칭은 누가 먼저 차단했든 상호작용을 막아야 하므로 양방향을 모두 본다.
     */
    override fun isBlockedBetween(memberId: Long, otherId: Long): Boolean =
        blocks.existsByBlockerIdAndBlockedId(memberId, otherId) ||
            blocks.existsByBlockerIdAndBlockedId(otherId, memberId)

    /**
     * 한 방에 판정한다. 목록 항목마다 [isBlockedBetween] 을 돌면 이 포트가 원격이 될 때
     * 항목 수만큼 왕복이 생긴다 — 그게 이 포트를 배치로 넓힌 이유다.
     *
     * 판정 규칙은 [isBlockedBetween] 과 같다(양방향). 조회 조건이 이미 (viewer, 후보) 쌍으로
     * 좁혀져 있으므로, 나온 두 컬럼에서 후보 id 만 골라내면 그게 곧 차단된 상대다 —
     * CASE 로 방향을 되돌릴 필요가 없다.
     */
    override fun blockedAmong(viewerId: Long, candidateIds: Collection<Long>): Set<Long> {
        // 자기 자신은 차단 대상이 될 수 없다(Block 이 생성 시점에 막는다). 넣어 봐야 조건만 넓어진다.
        val ids = candidateIds.toSet() - viewerId
        if (ids.isEmpty()) return emptySet()

        return dsl.select(QBlock.blockerId, QBlock.blockedId)
            .from(QBlock)
            .where(
                QBlock.blockerId.eq(viewerId).and(QBlock.blockedId.`in`(ids))
                    .or(QBlock.blockedId.eq(viewerId).and(QBlock.blockerId.`in`(ids)))
            )
            .fetch()
            .flatMap { listOfNotNull(it.get(QBlock.blockerId), it.get(QBlock.blockedId)) }
            .filterTo(mutableSetOf()) { it in ids }
    }

    override fun followingIds(memberId: Long): List<Long> =
        follows.findAllByFollowerId(memberId).map { it.followedId }

    /**
     * COUNT 두 번이다. 한 문장으로 합치려면 `where followed_id = ? or follower_id = ?` 에
     * 조건부 집계를 걸어야 하는데, 그러면 두 인덱스를 BitmapOr 로 묶은 뒤 CASE 를 계산하려고
     * 행마다 힙을 다시 읽는다. 팔로워가 백만인 회원에서 index-only scan 두 번이 훨씬 싸다.
     * 왕복 한 번 차이는 그 대가를 치를 값이 아니다.
     *
     * **두 숫자는 같은 시점이 아니다.** 이 포트는 곧 gRPC/HTTP 로 나가고, 그때는 호출자가
     * `@Transactional` 로 감싸도 원격 쪽 스냅샷은 묶이지 않는다. 즉 호출자가 스냅샷을
     * 보장할 방법이 없다 — 그러니 "필요하면 호출자가 감싸라"고 적어 두지 않는다.
     * 프로필 표시용 숫자라 그 정도 어긋남은 감수한다. 정확한 값이 필요한 요구가 생기면
     * 두 숫자를 한 응답으로 묶어 내는 포트를 이쪽에서 트랜잭션과 함께 만들어야 한다.
     */
    override fun counts(memberId: Long) = FollowQuery.CountInfo(
        followers = repo.countFollowers(memberId),
        followings = repo.countFollowings(memberId),
    )
}

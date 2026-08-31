package com.langlez.relationship.infrastructure

import com.langlez.relationship.contract.BlockQuery
import com.langlez.relationship.contract.FollowQuery
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import org.springframework.stereotype.Repository

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
) : BlockQuery, FollowQuery {

    /**
     * 차단 행은 단방향(blocker → blocked)으로만 저장된다.
     * 채팅·매칭은 누가 먼저 차단했든 상호작용을 막아야 하므로 양방향을 모두 본다.
     */
    override fun isBlockedBetween(memberId: Long, otherId: Long): Boolean =
        blocks.existsByBlockerIdAndBlockedId(memberId, otherId) ||
            blocks.existsByBlockerIdAndBlockedId(otherId, memberId)

    override fun followingIds(memberId: Long): List<Long> =
        follows.findAllByFollowerId(memberId).map { it.followedId }

    /**
     * COUNT 두 번이다. 한 문장으로 합치려면 `where followed_id = ? or follower_id = ?` 에
     * 조건부 집계를 걸어야 하는데, 그러면 두 인덱스를 BitmapOr 로 묶은 뒤 CASE 를 계산하려고
     * 행마다 힙을 다시 읽는다. 팔로워가 백만인 회원에서 index-only scan 두 번이 훨씬 싸다.
     * 왕복 한 번 차이는 그 대가를 치를 값이 아니다. 다만 두 COUNT 가 한 트랜잭션에 묶이는 건
     * **호출자가 트랜잭션을 열었을 때만** 참이다. 포트 자체는 트랜잭션을 열지 않는다 —
     * 트랜잭션 밖에서 부르면 두 숫자가 서로 다른 시점을 볼 수 있다.
     * 프로필 표시용이라 그 정도 어긋남은 감수한다. 정확한 스냅샷이 필요한 호출자가 생기면
     * 그쪽이 `@Transactional(readOnly = true)` 로 감싼다.
     */
    override fun counts(memberId: Long) = FollowQuery.CountInfo(
        followers = repo.countFollowers(memberId),
        followings = repo.countFollowings(memberId),
    )
}

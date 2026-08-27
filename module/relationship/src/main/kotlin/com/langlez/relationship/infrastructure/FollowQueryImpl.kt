package com.langlez.relationship.infrastructure

import com.langlez.core.FollowQuery
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import org.springframework.stereotype.Repository

/**
 * 팔로우 그래프 조회 포트 구현.
 *
 * echo 홈 타임라인이 쓴다. 구현이 없으면 타임라인이 통째로 503 이 되므로
 * relationship 모듈이 반드시 이 빈을 올려야 한다.
 */
@Repository
class FollowQueryImpl(
    private val jpa: FollowJpaRepository,
    private val repo: RelationshipRepository,
) : FollowQuery {

    override fun followingIds(memberId: Long): List<Long> =
        jpa.findAllByFollowerId(memberId).map { it.followedId }

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
    override fun counts(memberId: Long) = FollowQuery.Counts(
        followers = repo.countFollowers(memberId),
        followings = repo.countFollowings(memberId),
    )
}

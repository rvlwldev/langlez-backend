package com.langlez.relationship.infrastructure

import com.langlez.core.FollowQuery
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import org.springframework.stereotype.Repository

/**
 * 팔로우 그래프 조회 포트 구현.
 *
 * echo 홈 타임라인이 쓴다. 구현이 없으면 타임라인이 통째로 503 이 되므로
 * relationship 모듈이 반드시 이 빈을 올려야 한다.
 */
@Repository
class FollowQueryImpl(private val jpa: FollowJpaRepository) : FollowQuery {

    override fun followingIds(memberId: Long): List<Long> =
        jpa.findAllByFollowerId(memberId).map { it.followedId }
}

package com.langlez.follow.infrastructure

import com.langlez.follow.domain.Follow
import com.langlez.follow.domain.FollowRepository
import com.langlez.follow.domain.FollowRepository.Edge
import com.langlez.follow.infrastructure.jpa.FollowJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import com.langlez.follow.domain.QFollow.Companion.follow as QFollow

/**
 * 캐시를 두지 않는다. 팔로우는 쓰기가 드물지만 읽기도 목록 조회뿐이라
 * 캐시를 얹으면 무효화 지점만 늘어난다.
 */
@Repository
class FollowRepositoryImpl(
    private val jpa: FollowJpaRepository,
    private val dsl: JPAQueryFactory,
) : FollowRepository {

    override fun save(follow: Follow): Follow = jpa.save(follow)

    override fun find(followerId: Long, followedId: Long): Follow? =
        jpa.findByFollowerIdAndFollowedId(followerId, followedId)

    /** 벌크 DELETE 라 트랜잭션이 필요하다. 호출부가 트랜잭션 안이면 그대로 참여한다. */
    @Transactional
    override fun delete(followerId: Long, followedId: Long) {
        dsl.delete(QFollow)
            .where(QFollow.followerId.eq(followerId), QFollow.followedId.eq(followedId))
            .execute()
    }

    /** 목록에 필요한 건 상대 id 와 커서뿐이라 엔티티 전체가 아니라 두 컬럼만 읽는다. */
    override fun findFollowers(memberId: Long, size: Int, cursor: Long?): List<Edge> =
        dsl.select(QFollow.id, QFollow.followerId)
            .from(QFollow)
            .where(QFollow.followedId.eq(memberId), cursor?.let(QFollow.id::lt))
            .orderBy(QFollow.id.desc())
            .limit(size.toLong())
            .fetch()
            .map { Edge(it.get(QFollow.id)!!, it.get(QFollow.followerId)!!) }

    override fun findFollowings(memberId: Long, size: Int, cursor: Long?): List<Edge> =
        dsl.select(QFollow.id, QFollow.followedId)
            .from(QFollow)
            .where(QFollow.followerId.eq(memberId), cursor?.let(QFollow.id::lt))
            .orderBy(QFollow.id.desc())
            .limit(size.toLong())
            .fetch()
            .map { Edge(it.get(QFollow.id)!!, it.get(QFollow.followedId)!!) }

    /** IDX_MEMBER_FOLLOW_FOLLOWED 만 읽고 끝난다. 행을 안 읽으니 select 대상은 아무 컬럼이나 상관없다. */
    override fun countFollowers(memberId: Long): Long =
        dsl.select(QFollow.count())
            .from(QFollow)
            .where(QFollow.followedId.eq(memberId))
            .fetchOne() ?: 0

    override fun countFollowings(memberId: Long): Long =
        dsl.select(QFollow.count())
            .from(QFollow)
            .where(QFollow.followerId.eq(memberId))
            .fetchOne() ?: 0
}

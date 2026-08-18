package com.langlez.relationship.infrastructure.jpa

import com.langlez.relationship.domain.Follow
import org.springframework.data.jpa.repository.JpaRepository

interface FollowJpaRepository : JpaRepository<Follow, Long> {

    fun findByFollowerIdAndFollowedId(followerId: Long, followedId: Long): Follow?

    /** 홈 타임라인이 매번 호출한다. follower_id 인덱스를 타는 단순 조회다. */
    fun findAllByFollowerId(followerId: Long): List<Follow>
}

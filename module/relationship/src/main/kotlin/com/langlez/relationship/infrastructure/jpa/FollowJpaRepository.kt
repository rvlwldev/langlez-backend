package com.langlez.relationship.infrastructure.jpa

import com.langlez.relationship.domain.Follow
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface FollowJpaRepository : JpaRepository<Follow, Long> {
    fun findByFollowerIdAndFollowedId(followerId: Long, followedId: Long): Follow?
    fun countByFollowerId(followerId: Long): Long
    fun countByFollowedId(followedId: Long): Long
    fun deleteByFollowerIdAndFollowedId(followerId: Long, followedId: Long)

    @Query("SELECT f FROM Follow f WHERE f.followerId = :followerId AND (:cursor IS NULL OR f.id < :cursor) ORDER BY f.id DESC")
    fun findFollowings(followerId: Long, cursor: Long?, pageable: PageRequest): List<Follow>

    @Query("SELECT f FROM Follow f WHERE f.followedId = :followedId AND (:cursor IS NULL OR f.id < :cursor) ORDER BY f.id DESC")
    fun findFollowers(followedId: Long, cursor: Long?, pageable: PageRequest): List<Follow>
}

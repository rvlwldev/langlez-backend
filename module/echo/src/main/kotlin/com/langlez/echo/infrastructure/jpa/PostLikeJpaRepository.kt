package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.PostLike
import org.springframework.data.jpa.repository.JpaRepository

interface PostLikeJpaRepository : JpaRepository<PostLike, Long> {

    fun existsByPostIdAndMemberId(postId: Long, memberId: Long): Boolean

    fun deleteByPostIdAndMemberId(postId: Long, memberId: Long)
}

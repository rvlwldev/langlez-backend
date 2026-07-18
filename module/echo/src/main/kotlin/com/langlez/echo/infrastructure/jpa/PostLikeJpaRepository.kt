package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.PostLike
import org.springframework.data.jpa.repository.JpaRepository

interface PostLikeJpaRepository : JpaRepository<PostLike, Long> {
    fun findByMemberIdAndPostId(memberId: Long, postId: Long): PostLike?
    fun deleteByMemberIdAndPostId(memberId: Long, postId: Long)
}

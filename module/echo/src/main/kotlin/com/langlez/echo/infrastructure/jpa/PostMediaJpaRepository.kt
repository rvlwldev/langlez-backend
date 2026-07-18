package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.PostMedia
import org.springframework.data.jpa.repository.JpaRepository

interface PostMediaJpaRepository : JpaRepository<PostMedia, Long> {
    fun findByPostIdOrderBySequenceAsc(postId: Long): List<PostMedia>
    fun findByPostIdIn(postIds: List<Long>): List<PostMedia>
}

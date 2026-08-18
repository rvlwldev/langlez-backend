package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.PostMedia
import org.springframework.data.jpa.repository.JpaRepository

interface PostMediaJpaRepository : JpaRepository<PostMedia, Long> {
    fun findAllByPostIdIn(postIds: Collection<Long>): List<PostMedia>
}

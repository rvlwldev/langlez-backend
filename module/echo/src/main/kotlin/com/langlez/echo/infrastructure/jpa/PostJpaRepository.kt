package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.Post
import org.springframework.data.jpa.repository.JpaRepository

interface PostJpaRepository : JpaRepository<Post, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Post?
}

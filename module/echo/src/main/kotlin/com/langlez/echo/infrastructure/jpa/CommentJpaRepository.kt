package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.Comment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface CommentJpaRepository : JpaRepository<Comment, Long> {
    @Query("SELECT c FROM Comment c WHERE c.postId = :postId AND (:cursor IS NULL OR c.id < :cursor) ORDER BY c.id DESC")
    fun findByPost(postId: Long, cursor: Long?, pageable: Pageable): List<Comment>

    fun countByPostId(postId: Long): Long
}

package com.langlez.echo.infrastructure

import com.langlez.echo.domain.Comment
import com.langlez.echo.domain.CommentRepository
import com.langlez.echo.infrastructure.jpa.CommentJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class CommentRepositoryImpl(
    private val commentJpaRepository: CommentJpaRepository
) : CommentRepository {

    override fun save(comment: Comment): Comment =
        commentJpaRepository.save(comment)

    override fun findById(id: Long): Comment? =
        commentJpaRepository.findByIdOrNull(id)?.takeIf { it.deletedAt == null }

    override fun findByPost(postId: Long, cursor: Long?, size: Int): List<Comment> =
        commentJpaRepository.findByPost(postId, cursor, PageRequest.of(0, size))

    override fun countByPost(postId: Long): Long =
        commentJpaRepository.countByPostId(postId)
}

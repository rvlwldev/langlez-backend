package com.langlez.echo.domain

interface CommentRepository {
    fun save(comment: Comment): Comment
    fun findById(id: Long): Comment?
    fun findByPost(postId: Long, cursor: Long?, size: Int): List<Comment>
    fun countByPost(postId: Long): Long
}

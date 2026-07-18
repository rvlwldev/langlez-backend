package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.Post
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PostJpaRepository : JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE p.authorId IN :authorIds AND p.blinded = false AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    fun findFollowingFeed(authorIds: List<Long>, cursor: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p WHERE p.authorId NOT IN :excludeAuthorIds AND p.blinded = false AND (:cursor IS NULL OR p.likeCount < :cursorLikeCount OR (p.likeCount = :cursorLikeCount AND p.id < :cursor)) ORDER BY p.likeCount DESC, p.id DESC")
    fun findRecommendedFeedWithExcludes(excludeAuthorIds: List<Long>, cursor: Long?, cursorLikeCount: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p WHERE p.blinded = false AND (:cursor IS NULL OR p.likeCount < :cursorLikeCount OR (p.likeCount = :cursorLikeCount AND p.id < :cursor)) ORDER BY p.likeCount DESC, p.id DESC")
    fun findRecommendedFeedWithoutExcludes(cursor: Long?, cursorLikeCount: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p JOIN PostHashtag ph ON p.id = ph.postId JOIN Hashtag h ON ph.hashtagId = h.id WHERE h.name = :hashtag AND p.blinded = false AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    fun findByHashtag(hashtag: String, cursor: Long?, pageable: PageRequest): List<Post>
}

package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.Post
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional

interface PostJpaRepository : JpaRepository<Post, Long> {
    @Query("SELECT p FROM Post p WHERE p.authorId IN :authorIds AND p.blinded = false AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    fun findFollowingFeed(authorIds: List<Long>, cursor: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p WHERE p.authorId NOT IN :excludeAuthorIds AND p.blinded = false AND (:cursor IS NULL OR p.likeCount < :cursorLikeCount OR (p.likeCount = :cursorLikeCount AND p.id < :cursor)) ORDER BY p.likeCount DESC, p.id DESC")
    fun findRecommendedFeedWithExcludes(excludeAuthorIds: List<Long>, cursor: Long?, cursorLikeCount: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p WHERE p.blinded = false AND (:cursor IS NULL OR p.likeCount < :cursorLikeCount OR (p.likeCount = :cursorLikeCount AND p.id < :cursor)) ORDER BY p.likeCount DESC, p.id DESC")
    fun findRecommendedFeedWithoutExcludes(cursor: Long?, cursorLikeCount: Long?, pageable: PageRequest): List<Post>

    @Query("SELECT p FROM Post p JOIN PostHashtag ph ON p.id = ph.postId JOIN Hashtag h ON ph.hashtagId = h.id WHERE h.name = :hashtag AND p.blinded = false AND (:cursor IS NULL OR p.id < :cursor) ORDER BY p.id DESC")
    fun findByHashtag(hashtag: String, cursor: Long?, pageable: PageRequest): List<Post>

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount + 1 WHERE p.id = :postId")
    fun incrementLikeCount(postId: Long): Int

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.likeCount = p.likeCount - 1 WHERE p.id = :postId AND p.likeCount > 0")
    fun decrementLikeCount(postId: Long): Int

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.reportCount = p.reportCount + 1 WHERE p.id = :postId")
    fun incrementReportCount(postId: Long): Int

    @Transactional
    @Modifying(clearAutomatically = true)
    @Query("UPDATE Post p SET p.blinded = true, p.blindedAt = CURRENT_TIMESTAMP WHERE p.id = :postId AND p.reportCount >= :threshold AND p.blinded = false")
    fun blindIfThresholdReached(postId: Long, threshold: Int): Int
}

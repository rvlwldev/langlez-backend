package com.langlez.echo.infrastructure.jpa

import com.langlez.echo.domain.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query

interface PostJpaRepository : JpaRepository<Post, Long> {

    fun findByIdAndDeletedAtIsNull(id: Long): Post?

    /**
     * 좋아요 수는 엔티티에서 읽고-쓰지 않고 DB 에서 더한다.
     * 인기 글은 같은 행을 동시에 여러 요청이 건드려서 읽고-쓰기로는 증가가 유실된다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount + 1 where p.id = :id")
    fun increaseLikeCount(id: Long)

    /** 0 아래로 내려가지 않게 조건을 건다. 음수가 되면 되돌릴 방법이 없다. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Post p set p.likeCount = p.likeCount - 1 where p.id = :id and p.likeCount > 0")
    fun decreaseLikeCount(id: Long)
}

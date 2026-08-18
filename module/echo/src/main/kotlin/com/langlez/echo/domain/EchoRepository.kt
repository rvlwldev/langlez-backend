package com.langlez.echo.domain

import java.time.LocalDate

/**
 * 피드 저장소 포트.
 *
 * 목록 조회의 커서는 전부 **post/comment id** 다. `created_at` 을 커서로 쓰면 인스턴스마다 시계가
 * 조금씩 어긋나 같은 밀리초에 들어온 글의 순서가 페이지마다 뒤집히고, 그 틈으로 글이 통째로 건너뛴다.
 */
interface EchoRepository {

    fun save(post: Post): Post
    fun findPost(id: Long): Post?

    /** 작성자 id 목록에 속한 글을 id 내림차순으로. authorIds 가 비면 빈 목록. */
    fun findPosts(authorIds: Collection<Long>, size: Int, cursor: Long?): List<Post>
    fun findPostsByHashtag(tag: String, size: Int, cursor: Long?): List<Post>

    fun saveMedia(media: Collection<PostMedia>)
    fun findMedia(postIds: Collection<Long>): List<PostMedia>

    fun isLiked(postId: Long, memberId: Long): Boolean
    fun findLikedPostIds(memberId: Long, postIds: Collection<Long>): Set<Long>

    /**
     * 좋아요 등록/해제. `Post.likeCount` 는 엔티티에서 읽고-쓰지 않고 DB 에서 더하고 뺀다 —
     * 인기 글은 같은 행을 동시에 여러 요청이 건드려서 읽고-쓰기로는 증가가 유실된다.
     */
    fun addLike(postId: Long, memberId: Long)
    fun removeLike(postId: Long, memberId: Long)

    fun save(comment: Comment): Comment
    fun findComment(id: Long): Comment?
    fun findComments(postId: Long, size: Int, cursor: Long?): List<Comment>

    /** 없으면 만들어서 돌려준다. 같은 태그를 두 글이 동시에 처음 쓰면 유니크 제약에 걸리므로 구현이 흡수한다. */
    fun findOrCreateHashtags(names: Collection<String>): List<Hashtag>
    fun linkHashtags(postId: Long, hashtagIds: Collection<Long>)

    /** 하루치 태그별 글 수를 세어 `HashtagDailyStat` 에 반영한다. 같은 날짜로 여러 번 돌려도 결과가 같다. */
    fun aggregateDailyStats(date: LocalDate)
}

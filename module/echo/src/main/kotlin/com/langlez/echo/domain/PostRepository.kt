package com.langlez.echo.domain

interface PostRepository {
    fun save(post: Post): Post
    fun findById(id: Long): Post?
    fun findFollowingFeed(authorIds: List<Long>, cursor: Long?, size: Int): List<Post>
    fun findRecommendedFeed(excludeAuthorIds: List<Long>, cursor: Long?, size: Int): List<Post>
    fun findByHashtag(hashtag: String, cursor: Long?, size: Int): List<Post>

    // PostMedia
    fun saveMediaAll(mediaList: List<PostMedia>): List<PostMedia>
    fun findMediaByPostId(postId: Long): List<PostMedia>
    fun findMediaByPostIds(postIds: List<Long>): List<PostMedia>

    // PostLike
    fun saveLike(like: PostLike): PostLike
    fun findLike(memberId: Long, postId: Long): PostLike?
    fun deleteLike(memberId: Long, postId: Long)

    // PostReport
    fun saveReport(report: PostReport): PostReport
    fun findReport(reporterId: Long, postId: Long): PostReport?

    // Hashtag
    fun findHashtagByName(name: String): Hashtag?
    fun saveHashtag(hashtag: Hashtag): Hashtag

    // PostHashtag
    fun savePostHashtag(postHashtag: PostHashtag): PostHashtag
}

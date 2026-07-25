package com.langlez.echo.domain

interface PostRepository {
    fun save(post: Post): Post
    fun findById(id: Long): Post?
    fun findFollowingFeed(authorIds: List<Long>, cursor: Long?, size: Int): List<Post>
    fun findRecommendedFeed(excludeAuthorIds: List<Long>, cursor: Long?, size: Int): List<Post>
    fun findByHashtag(hashtag: String, cursor: Long?, size: Int): List<Post>

    // Admin
    fun findAllForAdmin(cursor: Long?, size: Int): List<Post>

    // PostMedia
    fun saveMediaAll(mediaList: List<PostMedia>): List<PostMedia>
    fun findMediaByPostIds(postIds: List<Long>): List<PostMedia>

    // PostLike
    fun saveLike(like: PostLike): PostLike
    fun findLike(memberId: Long, postId: Long): PostLike?
    fun deleteLike(memberId: Long, postId: Long)

    fun incrementLikeCount(postId: Long)
    fun decrementLikeCount(postId: Long)
    fun incrementReportCount(postId: Long)
    fun blindIfThresholdReached(postId: Long, threshold: Int)

    // Hashtag
    fun findHashtagByName(name: String): Hashtag?
    fun findHashtagsByNames(names: Collection<String>): List<Hashtag>
    fun saveHashtag(hashtag: Hashtag): Hashtag
    fun saveHashtagsAll(hashtags: List<Hashtag>): List<Hashtag>

    // PostHashtag
    fun savePostHashtag(postHashtag: PostHashtag): PostHashtag
    fun savePostHashtagsAll(postHashtags: List<PostHashtag>): List<PostHashtag>
}

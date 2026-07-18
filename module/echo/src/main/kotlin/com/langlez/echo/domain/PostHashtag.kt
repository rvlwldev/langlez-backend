package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(
    name = "post_hashtags",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_POST_HASHTAG", columnNames = ["post_id", "hashtag_id"])]
)
class PostHashtag(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "hashtag_id", nullable = false)
    val hashtagId: Long
)

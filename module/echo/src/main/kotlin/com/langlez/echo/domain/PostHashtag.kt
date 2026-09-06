package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(
    name = "post_hashtags",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_POST_HASHTAG", columnNames = ["post_id", "hashtag_id"])],
    // 해시태그 타임라인은 반대 방향(hashtag_id 선두)으로 들어온다. 실제 DDL 은 V18 이 만든다.
    indexes = [Index(name = "IDX_POST_HASHTAG_HASHTAG", columnList = "hashtag_id, post_id")],
)
class PostHashtag(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(name = "hashtag_id", nullable = false)
    val hashtagId: Long
)

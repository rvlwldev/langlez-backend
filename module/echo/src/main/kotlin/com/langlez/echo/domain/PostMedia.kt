package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(
    name = "post_media",
    // enrich 가 페이지마다 post_id in (...) 으로 부른다. 실제 DDL 은 V18 이 만든다.
    indexes = [Index(name = "IDX_POST_MEDIA_POST", columnList = "post_id")],
)
class PostMedia(
    @Id @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0,

    @Column(name = "post_id", nullable = false)
    val postId: Long,

    @Column(nullable = false)
    val url: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val type: Type,

    @Column(nullable = false)
    val sequence: Int
) {
    enum class Type {
        IMAGE, VIDEO
    }
}

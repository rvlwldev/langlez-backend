package com.langlez.echo.domain

import jakarta.persistence.*
import jakarta.persistence.GenerationType.IDENTITY

@Entity
@Table(name = "post_media")
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

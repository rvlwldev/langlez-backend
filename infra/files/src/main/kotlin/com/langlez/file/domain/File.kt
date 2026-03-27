package com.langlez.file.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "files")
class File(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @Column(nullable = false)
    val url: String = "",

    @Column(nullable = false)
    val originalName: String = "",

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val fileType: FileType = FileType.IMAGE,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),
) {
    enum class FileType { IMAGE, VIDEO, AUDIO }
}

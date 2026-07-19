package com.langlez.attachment.domain

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType.STRING
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType.IDENTITY
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(
    name = "attachments",
    indexes = [
        Index(name = "IDX_ATTACHMENT_SOURCE", columnList = "source_type, created_at, id"),
        Index(name = "IDX_ATTACHMENT_UPLOADER", columnList = "uploader_id, created_at, id"),
        Index(name = "IDX_ATTACHMENT_FILE_TYPE", columnList = "file_type, created_at, id"),
    ],
)
class Attachment(
    @Column(name = "uploader_id", nullable = false)
    val uploaderId: Long,

    @Enumerated(STRING)
    @Column(name = "source_type", nullable = false)
    val sourceType: SourceType,

    @Column(name = "source_id", nullable = false)
    val sourceId: String,

    @Enumerated(STRING)
    @Column(name = "file_type", nullable = false)
    val fileType: FileType,

    @Column(name = "storage_key", nullable = false, length = 500)
    val storageKey: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    enum class SourceType { CHAT, ECHO }
    enum class FileType { IMAGE, VIDEO, AUDIO }
}

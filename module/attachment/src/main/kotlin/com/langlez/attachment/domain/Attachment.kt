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
import jakarta.persistence.UniqueConstraint
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID

@Entity
@Table(
    name = "attachments",
    uniqueConstraints = [UniqueConstraint(name = "UNQ_ATTACHMENT_KEY", columnNames = ["key"])],
    indexes = [
        Index(name = "IDX_ATTACHMENT_SOURCE", columnList = "source_type, source_id, status"),
        Index(name = "IDX_ATTACHMENT_UPLOADER", columnList = "uploader_id, id"),
        Index(name = "IDX_ATTACHMENT_UNATTACHED", columnList = "status, created_at"),
    ]
)
class Attachment(
    val uploaderId: Long,
    @Enumerated(STRING) val sourceType: SourceType,
    var sourceId: String? = null,
    @Enumerated(STRING) val fileType: Type,

    @Column(length = 1000) val key: String,
    @Enumerated(STRING) var status: Status = Status.PENDING,

    var createdAt: Instant = Instant.now(),
    var attachedAt: Instant? = null,
    var deletedAt: Instant? = null
) {
    @Id
    @GeneratedValue(strategy = IDENTITY)
    val id: Long = 0

    fun attach(sourceId: String? = null) {
        require(this.status == Status.PENDING) { "common.bad-request" }

        this.sourceId = sourceId
        this.attachedAt = Instant.now()
        this.status = Status.ATTACHED
    }

    fun remove() {
        this.status = Status.DELETED
        this.deletedAt = Instant.now()
    }

    enum class SourceType { PROFILE, CHAT, ECHO }
    enum class Type { IMAGE, VIDEO, AUDIO }
    enum class Status { PENDING, ATTACHED, DELETED }

    companion object {
        fun create(uploaderId: Long, sourceType: SourceType, fileType: Type, key: String) =
            Attachment(uploaderId = uploaderId, sourceType = sourceType, fileType = fileType, key = key)

        fun buildKey(sourceType: SourceType, filename: String): String {
            val date = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER)
            return "${sourceType.name.lowercase()}/$date/${UUID.randomUUID()}_$filename"
        }

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

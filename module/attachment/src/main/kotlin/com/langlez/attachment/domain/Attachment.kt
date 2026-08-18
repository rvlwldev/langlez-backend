package com.langlez.attachment.domain

import com.langlez.exception.LanglezException
import org.springframework.http.HttpStatus
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
        Index(name = "IDX_ATTACHMENT_SOURCE", columnList = "source, source_id, status"),
        Index(name = "IDX_ATTACHMENT_UPLOADER", columnList = "uploader_id, id"),
        Index(name = "IDX_ATTACHMENT_UNATTACHED", columnList = "status, created_at"),
    ]
)
class Attachment(
    val uploaderId: Long,
    val source: String,
    var sourceId: String? = null,
    @Enumerated(STRING) val type: Type,

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
        // require 는 IllegalArgumentException 이라 advice 의 Exception 핸들러가 잡아 500 이 된다.
        if (this.status != Status.PENDING)
            throw LanglezException(HttpStatus.BAD_REQUEST, "common.bad-request")

        this.sourceId = sourceId
        this.attachedAt = Instant.now()
        this.status = Status.ATTACHED
    }

    fun remove() {
        this.status = Status.DELETED
        this.deletedAt = Instant.now()
    }

    enum class Type {
        IMAGE, VIDEO, AUDIO;

        val mime: String get() = "${name.lowercase()}/"
    }

    enum class Status { PENDING, ATTACHED, DELETED }

    companion object {
        fun create(uploaderId: Long, source: String, type: Type, key: String) =
            Attachment(uploaderId = uploaderId, source = source, type = type, key = key)

        fun buildKey(source: String, filename: String): String {
            val date = LocalDate.now(ZoneOffset.UTC).format(DATE_FORMATTER)
            return "${source.lowercase()}/$date/${UUID.randomUUID()}_$filename"
        }

        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    }
}

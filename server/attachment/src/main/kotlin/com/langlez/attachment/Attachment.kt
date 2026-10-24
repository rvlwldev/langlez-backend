package com.langlez.attachment

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "attachments")
class Attachment(
    @Column(name = "uploader_id", nullable = false, updatable = false)
    val uploaderId: UUID,

    @Column(nullable = false, updatable = false)
    val source: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    val type: AttachmentType,

    @Column(name = "batch_id", nullable = false, updatable = false)
    val batchId: UUID,

    @Column(nullable = false, updatable = false)
    val position: Int,

    @Column(name = "object_key", nullable = false, updatable = false)
    val objectKey: String,

    @Column(name = "content_type", nullable = false, updatable = false)
    val contentType: String,

    @Column(name = "size_bytes", nullable = false, updatable = false)
    val sizeBytes: Long,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: Instant,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    var id: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: AttachmentStatus = AttachmentStatus.PENDING

    @Column(name = "uploaded_at")
    var uploadedAt: Instant? = null
}

interface AttachmentRepository : JpaRepository<Attachment, UUID> {

    fun findByBatchIdAndUploaderIdOrderByPosition(batchId: UUID, uploaderId: UUID): List<Attachment>

    @Transactional
    @Modifying
    @Query(
        "update Attachment a set a.status = com.langlez.attachment.AttachmentStatus.UPLOADED, a.uploadedAt = :uploadedAt " +
            "where a.batchId = :batchId and a.status = com.langlez.attachment.AttachmentStatus.PENDING",
    )
    fun markUploaded(batchId: UUID, uploadedAt: Instant): Int
}

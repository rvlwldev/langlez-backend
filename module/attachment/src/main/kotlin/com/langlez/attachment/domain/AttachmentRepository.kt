package com.langlez.attachment.domain

import java.time.Duration

interface AttachmentRepository {
    fun save(attachment: Attachment): Attachment
    fun saveAll(attachments: List<Attachment>): List<Attachment>

    fun find(id: Long): Attachment?
    fun find(key: String): Attachment?
    fun find(sourceType: Attachment.SourceType, sourceId: String): List<Attachment>

    fun findAll(
        cursor: Long?,
        size: Int,
        sourceType: Attachment.SourceType? = null,
        status: Attachment.Status? = null,
        fileType: Attachment.Type? = null,
    ): List<Attachment>

    fun findAllUnattached(cutoffDuration: Duration = Duration.ofHours(1)): List<Attachment>
    fun deleteAll(attachments: List<Attachment>)
}

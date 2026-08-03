package com.langlez.attachment.domain

import java.time.Duration

interface AttachmentRepository {
    fun save(attachment: Attachment): Attachment
    fun saveAll(attachments: List<Attachment>): List<Attachment>

    fun find(id: Long): Attachment?
    fun find(key: String): Attachment?
    fun find(source: String, sourceId: String): List<Attachment>

    fun findAll(
        cursor: Long?,
        size: Int,
        source: String? = null,
        status: Attachment.Status? = null,
        fileType: Attachment.Type? = null,
    ): List<Attachment>

    fun findAllUnattached(cutoffDuration: Duration = Duration.ofHours(1)): List<Attachment>
    fun deleteAll(attachments: List<Attachment>)
}

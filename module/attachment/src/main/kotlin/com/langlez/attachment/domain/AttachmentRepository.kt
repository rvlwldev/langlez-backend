package com.langlez.attachment.domain

interface AttachmentRepository {
    fun saveAll(attachments: List<Attachment>): List<Attachment>

    fun findAll(
        cursor: Long?,
        size: Int,
        sourceType: Attachment.SourceType?,
        uploaderId: Long?,
        fileType: Attachment.FileType?,
    ): List<Attachment>
}

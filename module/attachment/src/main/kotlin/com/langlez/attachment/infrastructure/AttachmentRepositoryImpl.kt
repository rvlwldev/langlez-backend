package com.langlez.attachment.infrastructure

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.attachment.infrastructure.jpa.AttachmentJpaRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Repository

@Repository
class AttachmentRepositoryImpl(
    private val jpa: AttachmentJpaRepository,
) : AttachmentRepository {

    override fun saveAll(attachments: List<Attachment>): List<Attachment> = jpa.saveAll(attachments)

    override fun findAll(
        cursor: Long?,
        size: Int,
        sourceType: Attachment.SourceType?,
        uploaderId: Long?,
        fileType: Attachment.FileType?,
    ): List<Attachment> =
        jpa.findAllFiltered(cursor, sourceType, uploaderId, fileType, PageRequest.of(0, size))
}

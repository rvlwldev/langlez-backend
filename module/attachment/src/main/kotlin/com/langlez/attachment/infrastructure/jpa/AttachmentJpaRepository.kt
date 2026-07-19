package com.langlez.attachment.infrastructure.jpa

import com.langlez.attachment.domain.Attachment
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface AttachmentJpaRepository : JpaRepository<Attachment, Long> {

    @Query(
        """
        SELECT a FROM Attachment a
        WHERE (:cursor IS NULL OR a.id < :cursor)
          AND (:sourceType IS NULL OR a.sourceType = :sourceType)
          AND (:uploaderId IS NULL OR a.uploaderId = :uploaderId)
          AND (:fileType IS NULL OR a.fileType = :fileType)
        ORDER BY a.id DESC
        """,
    )
    fun findAllFiltered(
        cursor: Long?,
        sourceType: Attachment.SourceType?,
        uploaderId: Long?,
        fileType: Attachment.FileType?,
        pageable: Pageable,
    ): List<Attachment>
}

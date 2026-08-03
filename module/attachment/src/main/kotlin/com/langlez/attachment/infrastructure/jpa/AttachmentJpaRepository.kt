package com.langlez.attachment.infrastructure.jpa

import com.langlez.attachment.domain.Attachment
import org.springframework.data.jpa.repository.JpaRepository

interface AttachmentJpaRepository : JpaRepository<Attachment, Long> {
    fun findByKey(key: String): Attachment?
    fun findAllBySourceTypeAndSourceId(sourceType: Attachment.SourceType, sourceId: String): List<Attachment>
}

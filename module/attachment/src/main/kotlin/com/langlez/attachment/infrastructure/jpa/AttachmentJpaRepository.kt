package com.langlez.attachment.infrastructure.jpa

import com.langlez.attachment.domain.Attachment
import org.springframework.data.jpa.repository.JpaRepository

interface AttachmentJpaRepository : JpaRepository<Attachment, Long> {
    fun findByKey(key: String): Attachment?
    fun findAllBySourceAndSourceId(source: String, sourceId: String): List<Attachment>
}

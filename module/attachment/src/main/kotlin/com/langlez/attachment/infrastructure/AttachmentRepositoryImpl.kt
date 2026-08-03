package com.langlez.attachment.infrastructure

import com.langlez.attachment.domain.Attachment
import com.langlez.attachment.domain.AttachmentRepository
import com.langlez.attachment.domain.QAttachment.Companion.attachment
import com.langlez.attachment.infrastructure.jpa.AttachmentJpaRepository
import com.querydsl.core.types.dsl.BooleanExpression
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository
import java.time.Duration
import java.time.Instant

@Repository
class AttachmentRepositoryImpl(
    private val jpa: AttachmentJpaRepository,
    private val dsl: JPAQueryFactory,
) : AttachmentRepository {

    override fun save(attachment: Attachment): Attachment = jpa.save(attachment)
    override fun saveAll(attachments: List<Attachment>): List<Attachment> = jpa.saveAll(attachments)

    override fun find(id: Long): Attachment? = jpa.findByIdOrNull(id)
    override fun find(key: String): Attachment? = jpa.findByKey(key)
    override fun find(source: String, sourceId: String): List<Attachment> =
        jpa.findAllBySourceAndSourceId(source, sourceId)

    override fun findAll(
        cursor: Long?,
        size: Int,
        source: String?,
        status: Attachment.Status?,
        fileType: Attachment.Type?,
    ): List<Attachment> {
        val condition = listOfNotNull(
            cursor?.let { attachment.id.lt(it) },
            source?.let { attachment.source.eq(it) },
            status?.let { attachment.status.eq(it) },
            fileType?.let { attachment.fileType.eq(it) },
        ).reduceOrNull(BooleanExpression::and)

        return dsl.selectFrom(attachment)
            .where(condition)
            .orderBy(attachment.id.desc())
            .limit(size.toLong())
            .fetch()
    }

    override fun findAllUnattached(cutoffDuration: Duration): List<Attachment> {
        val cutoff = Instant.now().minus(cutoffDuration)

        return dsl.selectFrom(attachment)
            .where(
                attachment.status.eq(Attachment.Status.PENDING),
                attachment.createdAt.loe(cutoff)
            )
            .fetch()
    }

    override fun deleteAll(attachments: List<Attachment>) {
        jpa.deleteAll(attachments)
    }
}

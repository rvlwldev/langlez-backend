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
            fileType?.let { attachment.type.eq(it) },
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

    // 미첨부 정리 배치가 호출한다. Attachment 는 연관이 없어(cascade/orphanRemoval 없음)
    // 영속성 컨텍스트를 우회해도 고아 행이 안 생기므로 건당 DELETE 대신 배치로 지운다.
    override fun deleteAll(attachments: List<Attachment>) {
        jpa.deleteAllInBatch(attachments)
    }
}

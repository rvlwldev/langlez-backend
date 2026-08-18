package com.langlez.relationship.infrastructure

import com.langlez.relationship.domain.Block
import com.langlez.relationship.domain.Follow
import com.langlez.relationship.domain.RelationshipRepository
import com.langlez.relationship.domain.RelationshipRepository.Edge
import com.langlez.relationship.domain.Report
import com.langlez.relationship.infrastructure.jpa.BlockJpaRepository
import com.langlez.relationship.infrastructure.jpa.FollowJpaRepository
import com.langlez.relationship.infrastructure.jpa.ReportJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import com.langlez.relationship.domain.QBlock.Companion.block as QBlock
import com.langlez.relationship.domain.QFollow.Companion.follow as QFollow
import com.langlez.relationship.domain.QReport.Companion.report as QReport

/**
 * 캐시를 두지 않는다. 팔로우·차단은 쓰기가 드물지만 읽기도 목록 조회뿐이고,
 * 판정에 쓰이는 차단 여부는 `BlockQueryImpl` 이 단건 exists 로 따로 본다.
 */
@Repository
class RelationshipRepositoryImpl(
    private val follows: FollowJpaRepository,
    private val blocks: BlockJpaRepository,
    private val reports: ReportJpaRepository,
    private val dsl: JPAQueryFactory,
) : RelationshipRepository {

    override fun save(follow: Follow): Follow = follows.save(follow)

    override fun findFollow(followerId: Long, followedId: Long): Follow? =
        follows.findByFollowerIdAndFollowedId(followerId, followedId)

    /** 벌크 DELETE 라 트랜잭션이 필요하다. 호출부가 트랜잭션 안이면 그대로 참여한다. */
    @Transactional
    override fun deleteFollow(followerId: Long, followedId: Long) {
        dsl.delete(QFollow)
            .where(QFollow.followerId.eq(followerId), QFollow.followedId.eq(followedId))
            .execute()
    }

    /** 목록에 필요한 건 상대 id 와 커서뿐이라 엔티티 전체가 아니라 두 컬럼만 읽는다. */
    override fun findFollowers(memberId: Long, size: Int, cursor: Long?): List<Edge> =
        dsl.select(QFollow.id, QFollow.followerId)
            .from(QFollow)
            .where(QFollow.followedId.eq(memberId), cursor?.let(QFollow.id::lt))
            .orderBy(QFollow.id.desc())
            .limit(size.toLong())
            .fetch()
            .map { Edge(it.get(QFollow.id)!!, it.get(QFollow.followerId)!!) }

    override fun findFollowings(memberId: Long, size: Int, cursor: Long?): List<Edge> =
        dsl.select(QFollow.id, QFollow.followedId)
            .from(QFollow)
            .where(QFollow.followerId.eq(memberId), cursor?.let(QFollow.id::lt))
            .orderBy(QFollow.id.desc())
            .limit(size.toLong())
            .fetch()
            .map { Edge(it.get(QFollow.id)!!, it.get(QFollow.followedId)!!) }

    override fun save(block: Block): Block = blocks.save(block)

    override fun findBlock(blockerId: Long, blockedId: Long): Block? =
        blocks.findByBlockerIdAndBlockedId(blockerId, blockedId)

    @Transactional
    override fun deleteBlock(blockerId: Long, blockedId: Long) {
        dsl.delete(QBlock)
            .where(QBlock.blockerId.eq(blockerId), QBlock.blockedId.eq(blockedId))
            .execute()
    }

    override fun findBlocks(memberId: Long, size: Int, cursor: Long?): List<Edge> =
        dsl.select(QBlock.id, QBlock.blockedId)
            .from(QBlock)
            .where(QBlock.blockerId.eq(memberId), cursor?.let(QBlock.id::lt))
            .orderBy(QBlock.id.desc())
            .limit(size.toLong())
            .fetch()
            .map { Edge(it.get(QBlock.id)!!, it.get(QBlock.blockedId)!!) }

    override fun save(report: Report): Report = reports.save(report)

    override fun existsReport(
        reporterId: Long,
        sourceType: Report.SourceType,
        sourceId: String,
        triggerMessageId: String?,
    ): Boolean = dsl.selectOne()
        .from(QReport)
        .where(
            QReport.reporterId.eq(reporterId),
            QReport.sourceType.eq(sourceType),
            QReport.sourceId.eq(sourceId),
            // 트리거 메시지가 없는 신고는 NULL 로 저장된다. eq(null) 은 항상 false 라 IS NULL 로 비교해야 한다.
            triggerMessageId?.let(QReport.triggerMessageId::eq) ?: QReport.triggerMessageId.isNull,
        )
        .fetchFirst() != null
}

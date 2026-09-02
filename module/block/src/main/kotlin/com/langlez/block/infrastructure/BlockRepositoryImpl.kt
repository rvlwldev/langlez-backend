package com.langlez.block.infrastructure

import com.langlez.block.domain.Block
import com.langlez.block.domain.BlockRepository
import com.langlez.block.domain.BlockRepository.Edge
import com.langlez.block.infrastructure.jpa.BlockJpaRepository
import com.querydsl.jpa.impl.JPAQueryFactory
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import com.langlez.block.domain.QBlock.Companion.block as QBlock

/**
 * 캐시를 두지 않는다. 차단은 쓰기가 드물지만 판정에 쓰이는 차단 여부는
 * `BlockReaderImpl` 이 단건 exists 로 인덱스만 읽고 끝낸다.
 */
@Repository
class BlockRepositoryImpl(
    private val jpa: BlockJpaRepository,
    private val dsl: JPAQueryFactory,
) : BlockRepository {

    override fun save(block: Block): Block = jpa.save(block)

    override fun find(blockerId: Long, blockedId: Long): Block? =
        jpa.findByBlockerIdAndBlockedId(blockerId, blockedId)

    /** 벌크 DELETE 라 트랜잭션이 필요하다. 호출부가 트랜잭션 안이면 그대로 참여한다. */
    @Transactional
    override fun delete(blockerId: Long, blockedId: Long) {
        dsl.delete(QBlock)
            .where(QBlock.blockerId.eq(blockerId), QBlock.blockedId.eq(blockedId))
            .execute()
    }

    /** 목록에 필요한 건 상대 id 와 커서뿐이라 엔티티 전체가 아니라 두 컬럼만 읽는다. */
    override fun findBlocks(memberId: Long, size: Int, cursor: Long?): List<Edge> =
        dsl.select(QBlock.id, QBlock.blockedId)
            .from(QBlock)
            .where(QBlock.blockerId.eq(memberId), cursor?.let(QBlock.id::lt))
            .orderBy(QBlock.id.desc())
            .limit(size.toLong())
            .fetch()
            .map { Edge(it.get(QBlock.id)!!, it.get(QBlock.blockedId)!!) }
}

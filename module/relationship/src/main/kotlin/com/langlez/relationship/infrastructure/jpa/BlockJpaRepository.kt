package com.langlez.relationship.infrastructure.jpa

import com.langlez.relationship.domain.Block
import org.springframework.data.domain.PageRequest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface BlockJpaRepository : JpaRepository<Block, Long> {
    fun findByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Block?
    fun deleteByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long)

    @Query("SELECT b FROM Block b WHERE b.blockerId = :blockerId AND (:cursor IS NULL OR b.id < :cursor) ORDER BY b.id DESC")
    fun findBlocks(blockerId: Long, cursor: Long?, pageable: PageRequest): List<Block>
}

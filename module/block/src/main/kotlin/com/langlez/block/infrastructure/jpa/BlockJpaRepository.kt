package com.langlez.block.infrastructure.jpa

import com.langlez.block.domain.Block
import org.springframework.data.jpa.repository.JpaRepository

interface BlockJpaRepository : JpaRepository<Block, Long> {

    fun existsByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Boolean

    fun findByBlockerIdAndBlockedId(blockerId: Long, blockedId: Long): Block?
}

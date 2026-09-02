package com.langlez.block.domain

/**
 * 차단 저장소 포트.
 *
 * 목록은 커서 페이징이다. 정렬·커서 기준은 `created_at` 이 아니라 행 id 다 —
 * 인스턴스마다 시계가 어긋나면 같은 밀리초에 들어온 행이 페이지 경계에서 겹치거나 사라진다.
 */
interface BlockRepository {

    /** 목록 한 줄. `id` 는 다음 페이지 커서로 쓸 행 id, `memberId` 는 상대 회원 id. */
    data class Edge(val id: Long, val memberId: Long)

    fun save(block: Block): Block
    fun find(blockerId: Long, blockedId: Long): Block?
    fun delete(blockerId: Long, blockedId: Long)

    /** 내가 차단한 사람들 */
    fun findBlocks(memberId: Long, size: Int, cursor: Long?): List<Edge>
}

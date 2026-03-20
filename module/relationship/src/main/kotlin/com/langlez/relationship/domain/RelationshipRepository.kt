package com.langlez.relationship.domain

interface RelationshipRepository {

    // follow
    fun saveFollow(follow: Follow): Follow
    fun findFollow(followerId: Long, followedId: Long): Follow?
    fun findFollowings(followerId: Long, cursor: Long?, size: Int): List<Follow>
    fun findFollowers(followedId: Long, cursor: Long?, size: Int): List<Follow>
    fun countFollowings(followerId: Long): Long
    fun countFollowers(followedId: Long): Long
    fun deleteFollow(followerId: Long, followedId: Long)

    // block
    fun saveBlock(block: Block): Block
    fun findBlock(blockerId: Long, blockedId: Long): Block?
    fun findBlocks(blockerId: Long, cursor: Long?, size: Int): List<Block>
    fun deleteBlock(blockerId: Long, blockedId: Long)

}

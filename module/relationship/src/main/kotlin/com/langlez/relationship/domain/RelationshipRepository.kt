package com.langlez.relationship.domain

import com.langlez.member.domain.Member

interface RelationshipRepository {

    // follow
    fun saveFollow(follow: Follow): Follow
    fun findFollow(followerId: Long, followedId: Long): Follow?
    fun findFollowingMembers(id: Long): List<Member>
    fun findFollowerMembers(id: Long): List<Member>
    fun countFollowings(id: Long): Long
    fun countFollowers(id: Long): Long
    fun deleteFollow(followerId: Long, followedId: Long)

    // block
    fun saveBlock(block: Block): Block
    fun findBlock(blockerId: Long, blockedId: Long): Block?
    fun findBlockingMembers(blockerId: Long): List<Member>
    fun deleteBlock(blockerId: Long, blockedId: Long)

}
package com.langlez.relationship.application

class RelationshipEvent {
    data class Follow(val followerId: Long, val followingId: Long)
    data class Unfollow(val followerId: Long, val followingId: Long)
    data class Block(val blockerId: Long, val blockedId: Long)
    data class Unblock(val blockerId: Long, val blockedId: Long)
}
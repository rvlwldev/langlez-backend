package com.langlez.relationship.application

sealed interface RelationshipEvent {
    data class Follow(val followerId: Long, val followingId: Long) : RelationshipEvent
    data class Unfollow(val followerId: Long, val followingId: Long) : RelationshipEvent
    data class Block(val blockerId: Long, val blockedId: Long) : RelationshipEvent
    data class Unblock(val blockerId: Long, val blockedId: Long) : RelationshipEvent
}
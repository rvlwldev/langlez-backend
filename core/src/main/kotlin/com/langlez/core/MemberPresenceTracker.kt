package com.langlez.core

interface MemberPresenceTracker {
    fun markOnline(memberId: Long)
    fun isOnline(memberId: Long): Boolean
    fun countOnline(): Long

    fun areOnline(memberIds: Collection<Long>): Map<Long, Boolean> {
        return memberIds.associateWith { isOnline(it) }
    }
}

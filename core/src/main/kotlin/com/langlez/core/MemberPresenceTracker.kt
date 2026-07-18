package com.langlez.core

interface MemberPresenceTracker {
    fun markOnline(memberId: Long)
    fun isOnline(memberId: Long): Boolean
}

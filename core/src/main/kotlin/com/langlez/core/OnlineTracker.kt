package com.langlez.core

interface OnlineTracker {
    fun toOnline(id: Long)
    fun toOffline(id: Long)
    fun countOnline(): Long
    fun checkOnline(id: Long): Map<Long, Boolean>
    fun checkOnline(id: Collection<Long>): Map<Long, Boolean>
}

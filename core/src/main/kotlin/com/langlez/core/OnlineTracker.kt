package com.langlez.core

interface OnlineTracker {
    fun toOnline(id: Long)
    fun toOffline(id: Long)
    fun countOnline(): Long
    fun checkOnline(id: Long): Map<Long, Boolean>
    fun checkOnline(id: Collection<Long>): Map<Long, Boolean>

    /**
     * 화면(viewing) 상태. 핑은 "앱이 켜져 있다"까지만 말해서, 다른 화면을 보는 사람과
     * 그 방을 보고 있는 사람을 구분하지 못한다. 보고 있는 방에 알림을 쏘지 않으려면
     * STOMP 구독이 유일한 신호라 여기에 담는다.
     */
    fun recordViewing(memberId: Long, topic: String)
    fun clearViewing(memberId: Long, topic: String)
    fun clearAllViewing(memberId: Long)
    fun viewers(topic: String): Set<Long>
}

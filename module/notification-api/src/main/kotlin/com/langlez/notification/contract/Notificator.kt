package com.langlez.notification.contract

interface Notificator {
    fun notify(memberId: Long, type: String, title: String, body: String, data: Map<String, String> = emptyMap())

    fun notifyAll(
        memberIds: Collection<Long>,
        type: String,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    )
}

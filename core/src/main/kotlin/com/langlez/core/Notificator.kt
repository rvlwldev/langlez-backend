package com.langlez.core

interface Notificator {
    fun notify(memberId: Long, type: String, title: String, body: String, data: Map<String, String> = emptyMap())
}

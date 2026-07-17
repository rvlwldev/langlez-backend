package com.langlez.core

interface MessageQueue {
    fun publish(topic: String, key: String, payload: String)
}
